# 更多女仆交互 引擎白皮书 v1.0 （AI 生成）

## 目标
将“功能型随从”重构为“沙盒动态恋爱模拟系统”。引擎以“状态—情绪—心境—事件”的层级建模，支持多维刺激、启发式评分、记忆与社交上下文，并可平滑对接 DSL 与 MC 环境。

## 状态图（文字描述）
- 长期状态（慢变量）：
  - favor（好感）∈[0,1]
  - bond（羁绊）∈[0,1]
  - sincerity（真诚）∈[0,1]
  - novelty（新鲜）∈[0,1]
- 情绪（快变量，PAD）：
  - pleasure、arousal、dominance ∈[0,1]
- 心境分布（概率，Softmax）：
  - CALM、AFFECTIONATE、CURIOUS、JEALOUS、ANNOYED、BORED
- 记忆窗口：
  - 近 16 次事件，记录标签频率、熵多样性、多维刺激的指数平滑均值与方差
- 社交上下文：
  - rivals（对手数）、lastOtherAffection（最近观测到他人亲密度）、meanOtherFavor（他人平均好感）
- 事件：
  - name、valence[-1,1]、intensity[0,1]、tags、cooldown、stimulus(多维刺激向量)

关系：事件→启发式评分→非线性增量→更新长期状态→映射情绪→心境概率转移→写入记忆。

## 变量定义
- Stats：favor、bond、sincerity、novelty（均[0,1]）
- EmotionState：pleasure、arousal、dominance（均[0,1]）
- MoodModel 分布 P(Mood)，含六个心境
- Memory：
  - diversity∈[0,1]（基于标签计数的归一化熵）
  - frequency(tag)∈[0,1]
  - variance(axis)≥0、avgVariance≥0
  - distanceFromMean(stimulus)∈[0,1]
- SocialContext：rivals≥0、lastOtherAffection∈[0,1]、meanOtherFavor∈[0,1]
- StimulusAxis：CARE、AFFECTION、GIFT_VALUE、INTIMACY、PLAYFUL、WORK_HELPFULNESS、SOCIAL_EXPOSURE、TEASE_INTENSITY

## 更新顺序
1. memory.tick()
2. 计算评分 score(e, ctx)
   - base=valence×(0.7+0.3×intensity)
   - affinity=1+0.6×personality.affinity(tags)
   - recency=基于 cooldown 的惩罚乘子
   - diversity=0.8+0.4×memory.diversity()
   - novelty=0.5+0.5×stats.novelty()
   - synergy=0.8+0.4×mix(favor,bond,sincerity)+0.2×平均个性权重
   - stimulusMatch=0.8+0.4×dot(sensitivity(stats), stimulus)
   - varianceBoost=0.8+0.4×memory.distanceFromMean(stimulus)
   - jealousy=1-0.5×jealousyIndex(ctx, stats)
   - score=上述因子连乘
3. 非线性增量
   - g=logistic(2×score)
   - Δfavor=(g-0.5)×0.15×intensityGate
   - Δbond=(g-0.5)×0.10×bondGate
   - Δsincerity=(g-0.5)×0.04×sincerityGate
   - novelty ← clamp(novelty×0.92 + noveltyBoost×0.08)
4. 反刷屏与真诚调整
   - spamIndex 基于标签频率；过高时下调 sincerity
5. 写入记忆 memory.push(event)
6. 情绪映射 emotion(PAD) ← Stats 的线性组合并限幅
7. 心境概率转移
   - 以情绪、方差、spam、嫉妒、调戏轴、新鲜度构造 logits，经 softmax 得分布
   - 以 γ 混合历史分布与新分布

## 可暴露接口（DSL/外层可见）
- 读：
  - Stats：favor、bond、sincerity、novelty
  - Emotion：pleasure、arousal、dominance
  - Mood：CALM、AFFECTIONATE、CURIOUS、JEALOUS、ANNOYED、BORED 的概率与 topMood
  - Memory：diversity、frequency(tag)、distanceFromMean(axis)
  - Social：rivals、meanOtherFavor、lastOtherAffection、jealousyIndex（可作为派生读数）
- 写：
  - 仅通过 InteractionEvent 与 SocialContext 注入
  - 事件字段：name、valence、intensity、tags、cooldown、stimulus

## 不可暴露内部参数（保持稳定性与可替换性）
- logistic 形状、系数、门限系数
- 记忆指数平滑 α、心境融合 γ、窗口大小
- score 各因子的细节权重、内部 clamp 策略
- 内部敏感度映射 sensitivity(stats) 的具体公式

## 未来扩展点
- 环境上下文 EnvironmentContext（建议作为 Context 注入而非硬编码）：
  - weatherImpact、timeOfDayImpact、biomeAffinity、seasonModifier
  - 对 score 因子进行调制（如雨天提升嫉妒权重、略降愉悦基线）
- 长期记忆回溯系统（叙事化）：
  - 序列化关键记忆快照 MemorySnapshot（事件名、当时 favor/bond、情绪片段、时间戳）
  - 在特定心境与阈值下以小概率触发“回忆事件”，引导剧情分支
- 事件门槛与防速通：
  - 多条件门槛（例：告白需 bond>0.8 且 sincerity>0.7 且 mood.affectionate>0.6 且 diversity>0.4）
  - 非线性收益衰减与心境惯性（bond 越高，心境波动越小）
- 多主体扩展：
  - 记录“他人事件”对自身的间接影响，形成稳定的“后宫线”微观动力学

## 运行基准与观测标准
- 5000 次分段随机互动（含“刷屏”、“嫉妒注入”、“修复”阶段）
- 观测项：
  - 自然波动：情绪与心境分布存在慢—快变化
  - 情绪转折：topMood 出现阶段性切换
  - 偶发冲突：ANNOYED 或 JEALOUS 概率超过阈值的峰值计数
  - 长期稳定：后期 topMood 以 CALM/AFFECTIONATE 为主，波动收敛

## 与 MC 的集成边界
- FavorabilityManager 等外部系统仅在“边界层”对接
- 引擎保持纯 Java 可测试；MC 端以适配器读取/写入事件、上下文与可暴露数据

## 版本冻结建议
- 在 DSL 设计前冻结：
  - InteractionEvent 字段集合
  - 可暴露变量清单
  - 更新顺序与上下文注入点

---
以上为 v1.0 白皮书，作为 DSL 与内容系统的稳定接口约定与实现准绳。迁移至 MC 与后续 AI 版时，仅需在 Context 与事件构建侧新增适配，不应修改引擎核心。
