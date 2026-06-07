INSERT IGNORE INTO robot_profile (name, title, profession, level, power, personality)
WITH RECURSIVE seq(n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 132
),
robots AS (
    SELECT
        n,
        1 + MOD(n * 7, 60) AS lvl,
        CASE MOD(n, 3)
            WHEN 0 THEN 'warrior'
            WHEN 1 THEN 'ranger'
            ELSE 'mage'
        END AS profession
    FROM seq
)
SELECT
    CONCAT(
        CASE MOD(n, 16)
            WHEN 0 THEN '银冠'
            WHEN 1 THEN '晨星'
            WHEN 2 THEN '黑杉'
            WHEN 3 THEN '雾港'
            WHEN 4 THEN '赤铜'
            WHEN 5 THEN '霜铁'
            WHEN 6 THEN '月刃'
            WHEN 7 THEN '星砂'
            WHEN 8 THEN '旧塔'
            WHEN 9 THEN '风铃'
            WHEN 10 THEN '白焰'
            WHEN 11 THEN '暮潮'
            WHEN 12 THEN '烬石'
            WHEN 13 THEN '浅湾'
            WHEN 14 THEN '青灯'
            ELSE '鸢尾'
        END,
        CASE MOD(n, 12)
            WHEN 0 THEN '守夜人'
            WHEN 1 THEN '猎手'
            WHEN 2 THEN '秘术师'
            WHEN 3 THEN '铁卫'
            WHEN 4 THEN '商会客'
            WHEN 5 THEN '寻路者'
            WHEN 6 THEN '剑士'
            WHEN 7 THEN '旅法师'
            WHEN 8 THEN '斥候'
            WHEN 9 THEN '药剂师'
            WHEN 10 THEN '巡林者'
            ELSE '收账人'
        END,
        LPAD(n, 3, '0')
    ),
    CASE MOD(n, 10)
        WHEN 0 THEN '榜单常客'
        WHEN 1 THEN '副本冲层者'
        WHEN 2 THEN '商会倒爷'
        WHEN 3 THEN '强化赌徒'
        WHEN 4 THEN '低价猎人'
        WHEN 5 THEN '巡林老手'
        WHEN 6 THEN '新月旅人'
        WHEN 7 THEN '野队队长'
        WHEN 8 THEN '掉落收藏家'
        ELSE '公会成员'
    END,
    profession,
    lvl,
    850 + lvl * 560 + MOD(n * 241, 2600) + IF(MOD(n, 17) = 0, 6200, 0),
    CASE MOD(n, 9)
        WHEN 0 THEN '喜欢在世界频道报掉落'
        WHEN 1 THEN '关注排行榜变化'
        WHEN 2 THEN '经常蹲商会低价装备'
        WHEN 3 THEN '强化成功就会发言'
        WHEN 4 THEN '提醒新人别越级太深'
        WHEN 5 THEN '只聊副本路线'
        WHEN 6 THEN '喜欢比较装备词条'
        WHEN 7 THEN '常年刷日常任务'
        ELSE '偶尔吐槽金币不够用'
    END
FROM robots;

INSERT INTO chat_message (sender_name, kind, text)
SELECT
    name,
    'robot',
    CASE MOD(id, 10)
        WHEN 0 THEN CONCAT(title, ' 刚看完战力榜，前排又换人了。')
        WHEN 1 THEN '蛛影林地外缘掉率还行，刷两把就有东西看。'
        WHEN 2 THEN '商会今天的低级蓝装很快被扫走，想买要快。'
        WHEN 3 THEN '强化到 +4 以前还算稳，后面真的要看金币储备。'
        WHEN 4 THEN '越级副本别硬冲，推荐战力差太多会被一套带走。'
        WHEN 5 THEN '有人出护手吗？防御词条高一点的。'
        WHEN 6 THEN '刚整理完背包，一键出售终于清爽多了。'
        WHEN 7 THEN '日常清完记得领任务奖励。'
        WHEN 8 THEN '今天机器人队伍也在刷榜，别被挤下去了。'
        ELSE '世界频道已上线，公会大厅热闹起来了。'
    END
FROM robot_profile
ORDER BY id DESC
LIMIT 60;
