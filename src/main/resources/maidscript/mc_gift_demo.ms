on_event "builtin.player.gift_flower" {
  let quality = event.payload.quality_score
  let total = event.payload.total_count
  let distinct = event.payload.distinct_count
  let repeat = memory.count("gift.item." + event.payload.item_id, 9600)
  let spam = clamp(repeat / 8.0, 0.0, 0.55)

  let favor_gain = 0.03 + quality * 0.09 + clamp(total / 40.0, 0.0, 0.08)
  let bond_gain = 0.02 + quality * 0.06
  let novelty_gain = clamp(0.05 - memory.frequency("gift.any") * 0.02, 0.01, 0.08)

  if item.is("minecraft:nether_star") || item.is("minecraft:dragon_egg") {
    favor_gain = favor_gain + 0.10
    bond_gain = bond_gain + 0.08
    say("这份礼物太贵重了……我会好好珍惜它。")
    memory.add("gift.rare")
  } else if item.is("minecraft:diamond") || item.is("minecraft:emerald") {
    favor_gain = favor_gain + 0.07
    bond_gain = bond_gain + 0.05
    say("唔，好闪亮……你今天真的很用心。")
  } else if item.has("minecraft:poppy") || item.has("minecraft:dandelion") {
    favor_gain = favor_gain + 0.04
    bond_gain = bond_gain + 0.03
    say("花很漂亮，我会把今天记下来。")
    memory.add("gift.flower")
  } else if item.is("minecraft:cake") || item.is("minecraft:cookie") {
    favor_gain = favor_gain + 0.05
    bond_gain = bond_gain + 0.02
    say("甜点！我喜欢这种心意。")
  } else if item.is("minecraft:rotten_flesh") || item.is("minecraft:poisonous_potato") {
    favor_gain = favor_gain - 0.08
    bond_gain = bond_gain - 0.04
    novelty_gain = novelty_gain - 0.03
    say("嗯……这个味道有点可怕。")
    memory.add("gift.bad")
  } else {
    say("谢谢你准备的礼物，我收下啦。")
  }

  if distinct >= 4 {
    favor_gain = favor_gain + 0.03
    novelty_gain = novelty_gain + 0.02
    say("你准备得很细致，连搭配都想到了。")
  }

  if spam > 0.0 {
    favor_gain = favor_gain * (1.0 - spam)
    bond_gain = bond_gain * (1.0 - spam * 0.8)
  }

  if event.payload.rare {
    mood_bias { TRUST: 0.06, LOVE: 0.10 }
  } else {
    mood_bias { CALM: 0.03 }
  }

  apply_delta {
    favor: clamp(favor_gain, -0.10, 0.28)
    bond: clamp(bond_gain, -0.06, 0.18)
    novelty: clamp(novelty_gain, -0.05, 0.12)
    sincerity: clamp(0.01 + quality * 0.03, 0.0, 0.05)
  }

  memory.add("gift.any")
  memory.add("gift.item." + event.payload.item_id)
}