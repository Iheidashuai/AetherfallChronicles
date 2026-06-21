package com.mythicrealm.api.gameplay.ai;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;

@Component
public class TemplateDialogueProvider {
    private static final List<String> MARKET_REPLIES = List.of(
        "我刚看过商会，看词条比看颜色更准，别急着下手。",
        "市场这会儿波动挺大，先比强化等级，再看价格。"
    );
    private static final List<String> DUNGEON_REPLIES = List.of(
        "先刷稳过的本攒装备，危险档别硬冲。",
        "副本风险提示挺有用，差一点战力就先补装备。"
    );
    private static final List<String> ENHANCE_REPLIES = List.of(
        "强化别上头，金币留一点，失败的时候心态才稳。",
        "+7 往后我一般会停手一会儿，先把材料备足。"
    );
    private static final List<String> EVENT_REPLIES = List.of(
        "这波动静不小，频道里估计都看见了。",
        "这个战报可以，今晚大厅又有话题了。"
    );
    private static final List<String> DEFAULT_REPLIES = List.of(
        "收到，我也在看这件事，等下补一轮观察。",
        "这句有点意思，频道里应该有人会接着聊。",
        "先记下，等我刷完这一轮再回来细看。"
    );

    public String fallbackReply(String text, String triggerKind) {
        String safeText = text == null ? "" : text;
        if (triggerKind != null && triggerKind.startsWith("event")) {
            return pick(EVENT_REPLIES);
        }
        if (safeText.contains("市场") || safeText.contains("装备")) {
            return pick(MARKET_REPLIES);
        }
        if (safeText.contains("副本") || safeText.contains("Boss") || safeText.contains("boss")) {
            return pick(DUNGEON_REPLIES);
        }
        if (safeText.contains("强化")) {
            return pick(ENHANCE_REPLIES);
        }
        return pick(DEFAULT_REPLIES);
    }

    private String pick(List<String> pool) {
        return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }
}
