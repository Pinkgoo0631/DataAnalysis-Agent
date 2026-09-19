# 从 EvidenceRecallNode 学 Reactor

> 本文只讲 `EvidenceRecallNode` 及其直接调用链中真实出现的 Reactor 知识。目标不是背 API，而是能够读懂、调试和修改这个节点的流式代码。

## 1. 先看结论：这个节点返回的不是证据，而是一条“将来产生证据”的流

`EvidenceRecallNode.apply(...)` 最终返回：

```java
return Map.of(EVIDENCE, generator.concatWith(evidenceFlux));
```

其中 `EVIDENCE` 对应的值并不是最终的 `String`，而是：

```java
Flux<GraphResponse<StreamingOutput>>
```

可以先建立一个最重要的心智模型：

- 普通方法返回的是一个已经算出的值；
- Reactor 方法经常返回一份“数据如何产生、加工和结束”的执行说明；
- 只有订阅者订阅这份说明之后，数据才会沿着流水线流动。

因此，调用 `apply(...)` 主要是在**组装流水线**。真正消费这条流水线的地方在 `GraphServiceImpl`：

```java
Disposable disposable = nodeOutputFlux.subscribe(
    output -> handleNodeOutput(graphRequest, output),
    error -> handleStreamError(graphRequest, error),
    () -> handleStreamComplete(graphRequest)
);
```

`subscribe(...)` 的三个回调分别对应：

1. 收到一项数据，即 `onNext`；
2. 流发生错误，即 `onError`；
3. 流正常结束，即 `onComplete`。

一条流只会以 `onError` 或 `onComplete` 中的一个结束，结束之后不能再发送数据。

---

## 2. Flux 大概是什么

`Flux<T>` 表示一个异步序列，它可以产生 **0 到 N 个** `T`：

```text
onNext(T) → onNext(T) → ... → onComplete()
                            或
onNext(T) → onNext(T) → ... → onError(Throwable)
```

在本节点中：

```java
Flux<ChatResponse> responseFlux = llmService.callUser(prompt);
```

大模型不是等整段答案生成完才返回，而是持续产生多个 `ChatResponse` 分片。因此这里使用能表达多项数据的 `Flux<ChatResponse>`。

与它经常一起出现的是 `Mono<T>`：

- `Flux<T>`：0 到 N 项；
- `Mono<T>`：0 或 1 项。

本项目的 `FluxUtil` 在流的最后使用了 `Mono.fromSupplier(...)`，因为“最终状态”只需要产生一次：

```java
streamingFlux.concatWith(
    Mono.fromSupplier(() -> GraphResponse.done(resultSupplier.get()))
)
```

### 创建 Flux 不等于执行 Flux

这是阅读该节点最关键的一点。下面这行只得到一个 Publisher：

```java
Flux<ChatResponse> responseFlux = llmService.callUser(prompt);
```

后续的 `map`、`concatWith`、`doOnNext` 也主要是在描述处理关系。上游是否已经进行少量准备工作由具体实现决定，但响应数据的消费由订阅启动。

可以把 `Flux` 想成一张传送带设计图，把 `subscribe` 想成打开传送带电源。

---

## 3. 本节点用到的 Flux 创建方法

### `Flux.just(...)`：发出给定数据，然后正常结束

节点用它创建查询重写前后的固定提示：

```java
Flux.just(
    ChatResponseUtil.createResponse("正在查询重写以更好召回evidence..."),
    ChatResponseUtil.createPureResponse(TextType.JSON.getStartSign())
)
```

它订阅后的信号类似：

```text
onNext("正在查询重写...")
onNext(JSON开始标记)
onComplete()
```

适用场景：已经拥有一项或几项固定值，希望把它们接入响应式流水线。

### `Flux.empty()`：不发数据，直接正常结束

```java
Flux.empty()
```

它并不是 `null`，而是一条合法的空流：

```text
onComplete()
```

`evidenceFlux` 不需要额外的前置、后置消息，所以把两个空流传给工具方法。用空流而不是 `null`，组合代码就不必到处做空值判断。

---

## 4. 本节点用到的 Flux 操作符

### `map`：一项对一项地转换

```java
evidenceDisplaySink.asFlux()
    .map(ChatResponseUtil::createPureResponse)
```

Sink 发出的是 `String`，而 `FluxUtil` 接受的是 `ChatResponse`。`map` 对每一条文本执行一次转换：

```text
String 1 ──map──> ChatResponse 1
String 2 ──map──> ChatResponse 2
```

`map` 不负责启动流，也不会凭空增加异步线程。

### `doOnNext`：观察每一项，并执行副作用

`FluxUtil.createStreamingGenerator(...)` 使用它收集 LLM 分片：

```java
sourceFlux = sourceFlux.doOnNext(
    response -> collectedResult.append(ChatResponseUtil.getText(response))
);
```

数据仍然原样向下游传递，但每个分片还会被追加到 `StringBuilder`。所以它适合日志、统计、收集等“旁路动作”。如果目标是把数据变成另一种数据，应使用 `map`。

### `filter`：只让满足条件的项通过

`FluxUtil` 会过滤没有有效输出的 `ChatResponse`：

```java
.filter(response -> response != null
    && response.getResult() != null
    && response.getResult().getOutput() != null)
```

被过滤的项不会传给下游，但这不代表流失败。

### `concatWith` / `Flux.concat`：严格按顺序连接流

节点最后使用：

```java
generator.concatWith(evidenceFlux)
```

语义是：

1. 先订阅并消费完 `generator`；
2. 只有 `generator` 正常完成，才订阅 `evidenceFlux`；
3. 两段数据不会交错。

这正好满足 UI 的展示顺序：先显示查询重写过程，再显示证据检索结果。

`FluxUtil` 中的：

```java
Flux.concat(preFlux, sourceFlux, sufFlux)
```

也是同样的顺序组合思想。

注意：如果前一条流永远不完成，后一条流永远不会被订阅；如果前一条流把错误继续向下传播，通常也不会进入后一条流。

### `onErrorResume`：把错误信号换成另一条流

`FluxUtil` 使用：

```java
.onErrorResume(error -> Flux.just(GraphResponse.error(error)))
```

它把原本会以 `onError` 终止的情况转换为一项普通的 `GraphResponse.error(...)`，随后替代流正常结束。这叫“错误恢复”，不是简单记录日志。

---

## 5. Sinks 是什么，为什么这里需要它

Reactor 的类名是 `Sinks`，本节点创建的具体对象是 `Sinks.Many<String>`：

```java
Sinks.Many<String> evidenceDisplaySink =
    Sinks.many().multicast().onBackpressureBuffer();
```

可以这样区分：

- `Flux` 更像“下游订阅后，由既定流水线拉起数据生产”；
- `Sinks.Many` 提供一个命令式入口，业务代码可以在任意合适的时刻主动发送 0 到 N 条数据；
- `sink.asFlux()` 把这个命令式入口暴露成下游可以订阅的 `Flux`。

本节点的 `getEvidences(...)` 是普通同步方法，而且会根据检索结果发送数量不固定的展示消息：

```java
sink.tryEmitNext("重写后查询：\n");
sink.tryEmitNext(standaloneQuery + "\n");
sink.tryEmitNext("正在获取证据...");
// 每找到一篇文档，还会继续 emit 一条摘要
sink.tryEmitComplete();
```

Sink 在这里充当“普通命令式代码”和“响应式输出流”之间的桥。

### 三种终止/发送方法

```java
sink.tryEmitNext(value);  // 发出一项数据
sink.tryEmitComplete();   // 正常结束
sink.tryEmitError(error); // 以错误结束
```

`tryEmit...` 不会像普通 `throw` 那样自动中断当前 Java 方法，它会返回一个 `Sinks.EmitResult`，告诉调用者发送是否成功。本节点没有检查该返回值；维护这段代码时要知道，流已终止、发生并发发送、订阅者取消等情况都可能使发送失败。

### `many()`、`multicast()` 分别表示什么

```java
Sinks.many().multicast().onBackpressureBuffer()
```

- `many()`：可以发出多项；
- `multicast()`：允许多个订阅者看到订阅之后的共享数据；
- `onBackpressureBuffer()`：消费者暂时处理不过来时，先在缓冲区中保存待消费数据。

它是一种热源：数据由 `tryEmit...` 主动产生，不是为每个订阅者重新执行一次生产逻辑。这个类型允许在第一个订阅者到来前进行 warm-up 缓冲；第一个订阅者可以接收此前缓冲的内容。但它不是“永久保存并向任意晚到订阅者重放全部历史”的存储器。

### 背压是什么

背压解决的是“生产速度大于消费速度”时如何处理：

```text
生产者：████████████████
消费者：██████
             ↑ 差值需要等待、丢弃、报错或缓冲
```

这里选择了缓冲。它不代表自动创建新线程，也不代表缓冲无限大，更不能代替容量规划。证据摘要条数通常有限，所以此处的压力一般不大。

---

## 6. EvidenceRecallNode 的完整执行时间线

把 `EvidenceRecallNode` 和 `FluxUtil` 合在一起看，真实顺序如下：

```text
apply(state)
  │
  ├─ 读取 question、agentId，构造 prompt
  ├─ 得到 responseFlux
  ├─ 创建 evidenceDisplaySink
  ├─ 组装 generator 和 evidenceFlux
  └─ 返回 generator.concatWith(evidenceFlux)
          │
          │ 之后，GraphServiceImpl 订阅
          ▼
  1. generator 发出“正在查询重写...”和 JSON 开始标记
  2. responseFlux 持续发出 LLM ChatResponse 分片
  3. doOnNext 一边向 UI 转发分片，一边把文本拼进 collectedResult
  4. 发出 JSON 结束标记和“查询重写完成”
  5. generator 的 Mono.fromSupplier(...) 执行 resultSupplier
  6. resultSupplier 调用 getEvidences(collectedResult, ...)
  7. getEvidences 同步检索文档，并通过 Sink 发送展示文本
  8. getEvidences 调用 tryEmitComplete()
  9. generator 发出 GraphResponse.done(...) 并完成
 10. concatWith 此时才订阅 evidenceFlux
 11. evidenceFlux 从 Sink 对应的 Flux 读取已缓冲文本并向 UI 输出
 12. evidenceFlux 完成，整条节点流完成
```

第 5 步尤其容易漏看。`getEvidences(...)` 并不是直接在 `apply(...)` 中执行，而是被包在 `resultSupplier` 中，到前面所有 LLM 分片消费完成之后才执行。

这也是 `Mono.fromSupplier(...)` 的价值：延迟计算最终结果，直到订阅过程真正走到这个位置。

---

## 7. 为什么同时需要 generator 和 evidenceFlux

这两条流承担不同职责：

| 流 | 数据来源 | UI 内容 | 最终状态 |
|---|---|---|---|
| `generator` | LLM 的 `responseFlux` | 查询重写过程和模型分片 | 聚合 LLM 文本，并触发证据检索 |
| `evidenceFlux` | `evidenceDisplaySink.asFlux()` | 重写后的查询、检索进度、证据摘要 | 返回同一个 `resultMap` |

`resultMap` 由两条流共享：

```java
final Map<String, Object> resultMap = new HashMap<>();
```

在 `generator` 结束前，`getEvidences(...)` 将最终证据放进去；随后 `evidenceFlux` 继续使用这个结果。这里的共享可变状态之所以暂时可控，依赖于 `concatWith` 建立的严格先后关系。

---

## 8. 错误、完成和取消要分清

### 同步抛错与流内错误不同

如果下面的方法在返回 `Flux` 之前就直接抛异常：

```java
llmService.callUser(prompt)
```

那么 `apply(...)` 当场失败，连完整流水线都没有组装出来。

如果它成功返回了 `Flux`，但该 Flux 在订阅后发出 `onError`，这属于流内错误，会走响应式错误处理操作符。

### 证据检索失败

`getEvidences(...)` 捕获异常后执行：

```java
sink.tryEmitError(e);
return Map.of(EVIDENCE, "");
```

`finally` 中仍会尝试 `tryEmitComplete()`，但一个 Sink 一旦已经以错误结束，就不能再改成正常结束。因此后一次 emit 会失败；这也是理解 `EmitResult` 很重要的原因。

### 异步 LLM 错误的边界

当前 `FluxUtil` 会把异步错误转换成 `GraphResponse.error(...)`。如果错误发生在触发 `getEvidences(...)` 之前，证据 Sink 可能还没有收到完成信号，而外层 `concatWith` 随后会订阅它。维护此处时，应专门验证这条路径能否及时终止，避免下游一直等待。

这不是 Reactor 的自动保证：每个手工创建的 Sink 都需要明确设计成功、失败和取消时如何终止。

### 取消与 Disposable

`subscribe(...)` 返回 `Disposable`。客户端断开或任务停止时，项目会调用 `dispose()`，用于取消上游工作。取消不是 `onComplete`；不要依赖完成回调去处理所有清理逻辑。

---

## 9. 这条流最终如何到达浏览器

节点内部的流只是整个链路的一段：

```text
LLM Flux<ChatResponse>
        ↓
EvidenceRecallNode Flux<GraphResponse<StreamingOutput>>
        ↓
compiledGraph.stream(...) / GraphServiceImpl.subscribe(...)
        ↓
Sinks.Many<ServerSentEvent<GraphNodeResponse>>
        ↓ asFlux()
GraphController 返回 Flux<ServerSentEvent<...>>
        ↓
浏览器 SSE（text/event-stream）
```

这里出现了两类 Sink：

- 节点内的 `multicast` Sink：把证据检索的命令式消息接入节点流；
- Controller 侧的 `unicast` Sink：为一个 SSE 客户端提供事件流。

`unicast` 面向单个订阅者，`multicast` 可面向多个订阅者。选择哪一种，应由实际订阅者数量决定。

---

## 10. 如何正确测试 Reactor 代码

现有 `EvidenceRecallNodeTest` 中的大多数测试只执行：

```java
Map<String, Object> result = evidenceRecallNode.apply(state);
assertNotNull(result.get(EVIDENCE));
```

这只能证明“成功组装并返回了一条流”，不能证明流中的 LLM 分片、向量检索、Sink 消息、完成或错误真的发生了。因为没有订阅，很多延迟逻辑不会执行。

响应式测试通常使用 `reactor-test` 提供的 `StepVerifier`。概念示例：

```java
@SuppressWarnings("unchecked")
Flux<GraphResponse<StreamingOutput>> flux =
    (Flux<GraphResponse<StreamingOutput>>) result.get(EVIDENCE);

StepVerifier.create(flux)
    .recordWith(ArrayList::new)
    .thenConsumeWhile(ignored -> true)
    .verifyComplete();
```

实际测试还应根据 `GraphResponse` 的 API 断言：

- 查询重写提示先出现；
- 证据摘要后出现；
- 向量库方法在订阅后被调用；
- 成功路径在有限时间内完成；
- LLM 异步错误、向量库错误和取消路径不会悬挂。

排查“代码看起来执行了，但 Mock 没被调用”时，第一件事就是确认测试是否订阅了 Flux。

---

## 11. 修改这个节点时的实用规则

### 规则一：先判断自己是在“组装”还是在“执行”

看到 `map`、`filter`、`concatWith`、`Mono.fromSupplier` 时，先问：这段 lambda 现在执行，还是订阅后执行？

### 规则二：每个手工 Sink 都要覆盖所有终止路径

成功要 `complete`，失败要 `error`，取消要能停止上游；还要处理 `tryEmit...` 的失败结果。

### 规则三：需要先后顺序就用 `concat`，允许交错才考虑 `merge`

本节点必须“重写结束后再展示证据”，所以 `concatWith` 很合适。如果换成合并型操作符，UI 消息可能交错。

### 规则四：不要把阻塞调用误认为响应式调用

`retrieveDocuments(...)` 返回普通 `List<Document>`，数据库 Mapper 也属于同步调用。即使它们被放在 Reactor 回调里，也不会自动变成非阻塞操作，更不会自动切换线程。

如果这些调用耗时明显，需要显式评估调度器隔离；不能仅因为外层类型是 `Flux` 就认为整条链路是非阻塞的。

### 规则五：不要在同一条流上随意重复订阅

冷流的每次订阅可能重新调用上游；热 Sink 又不会为晚到订阅者重放所有历史。调试时为了“看一下结果”多订阅一次，可能改变行为。

---

## 12. 最小记忆卡

| 概念 | 一句话记忆 | 本节点中的位置 |
|---|---|---|
| `Flux<T>` | 0 到 N 项的异步序列 | LLM 分片、节点输出 |
| `Mono<T>` | 0 或 1 项的异步序列 | 延迟生成最终 `done` 响应 |
| 订阅 | 启动并消费流水线 | `GraphServiceImpl.subscribe(...)` |
| `Flux.just` | 发出固定值后完成 | 查询重写前后提示 |
| `Flux.empty` | 不发值，直接完成 | 无需前后提示时占位 |
| `map` | 每项转换一次 | `String → ChatResponse` |
| `doOnNext` | 观察每项并做副作用 | 聚合 LLM 文本、统计 token |
| `filter` | 只保留满足条件的项 | 过滤无输出的响应 |
| `concatWith` | 前一条完成后再订阅后一条 | 先重写，后展示证据 |
| `onErrorResume` | 用另一条流替代错误 | 错误转 `GraphResponse.error` |
| `Sinks.Many` | 命令式地向响应式流发多项数据 | 发送证据检索展示消息 |
| `asFlux()` | 把 Sink 的输出交给订阅者 | 构造 `evidenceFlux` |
| 背压 | 协调生产者和消费者的速度差 | `onBackpressureBuffer()` |
| `Disposable` | 订阅句柄，可用于取消 | 停止图执行 |

## 13. 建议的阅读顺序

1. `EvidenceRecallNode.apply(...)`：看两条流怎样创建和连接；
2. `FluxUtil.createStreamingGenerator(...)`：看分片如何收集、转换和结束；
3. `GraphServiceImpl.subscribeToFlux(...)`：找到真正的订阅点；
4. `GraphController.streamSearch(...)`：看 Sink 怎样转为 SSE Flux；
5. `EvidenceRecallNodeTest`：对照“只组装”和“真正订阅”的差异。

读完后，如果能不看代码复述第 6 节的 12 步时间线，就已经掌握了维护这个节点所需的大部分 Reactor 基础。
