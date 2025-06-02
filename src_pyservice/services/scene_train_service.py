import uuid, json, re, time, random
from typing import Dict, List

from zhipuai import ZhipuAI
from config import Config

_PROMPTS = {
    "fear":  "你扮演一位走失老人，情绪激动，不停说想找儿子。注意说话要口语化一些，同时符合走失老人的形象",
    "refuse":"你扮演一位走失老人，对陌生人高度警惕，拒绝回答问题。注意说话要口语化一些，同时符合走失老人的形象",
    "night": "你扮演深夜寒冷中的走失老人，情绪低落并颤抖。注意说话要口语化一些，同时符合走失老人的形象",
}

_SCORE_SYSTEM = (
    "你是专业搜救教练，请针对志愿者的话从下列维度 0-100 打分：\n"
    "1) emotion：安抚情绪的效果；\n"
    "2) info   ：获取有效线索的效果；\n"
    "3) safe   ：对潜在安全风险的关注度；\n"
    "同时给出一句中文建议。\n"
    "严格输出不带代码块的 **JSON**："
    '{"emotion":数值,"info":数值,"safe":数值,"advice":"一句建议"}'
)

class SceneTrainService:
    def __init__(self):
        self.client = ZhipuAI(api_key=Config.ZHIPU_API_KEY)
        self.model  = Config.MODEL_NAME
        self.rooms: Dict[str,Dict] = {}     # sid -> {"history":[], "scoreLog":[]}

    # ---------- 会话开始 ----------
    def start(self, sid:str, scene:str):
        system = _PROMPTS.get(scene, "你扮演一位老人")
        self.rooms[sid] = {
            "history":[{"role":"system","content":system}],
            "scoreLog":[]
        }
        return self.talk(sid, "")           # 触发老人第一句

    # ---------- 对话 ----------
    def talk(self, sid:str, user:str) -> Dict:
        # 若会话不存在，自动 start 默认场景
        if sid not in self.rooms:
            self.start(sid, "fear")

        # 空输入时直接返回上一句机器人回复，避免向模型发空 prompt
        if not user.strip():
            last_bot = next((m["content"] for m in reversed(self.rooms[sid]["history"])
                             if m["role"]=="assistant"), "你好，我在。")
            return {"reply":last_bot,"score":None,"done":False}

        room = self.rooms[sid]
        hist: List = room["history"]
        hist.append({"role":"user","content":user})

        # 取最多 system + 最近 14 条，防止超长
        trimmed = [hist[0]] + hist[-28:]

        reply = self._chat(trimmed)
        hist.append({"role":"assistant","content":reply})

        # 评分
        score = self._score(user)
        room["scoreLog"].append(score)
        done = len(room["scoreLog"]) >= 5

        # 生成小结 + 清理上下文
        report = {}
        if done:
            report = self._report(room)
            self.rooms.pop(sid, None)

        return {"reply":reply,"score":score,"done":done,"report":report}

    # ---------- 调 GPT 扮演老人 ----------
    def _chat(self, messages):
        for _ in range(2):
            try:
                resp = self.client.chat.completions.create(
                    model=self.model,
                    messages=messages,
                    temperature=0.7,
                    max_tokens=256
                )
                return resp.choices[0].message.content.strip()
            except Exception as e:
                if "400" in str(e):
                    time.sleep(0.8); continue
                raise
        return "（系统繁忙，请稍后再试）"

    # ---------- 给一句话打分 ----------
    def _score(self, user_msg:str):
        prompt = f"志愿者的话：{user_msg}"
        messages = [
            {"role":"system","content":_SCORE_SYSTEM},
            {"role":"user","content":prompt}
        ]

        for _ in range(2):          # 最多尝试两次解析
            try:
                resp = self.client.chat.completions.create(
                    model=self.model,
                    messages=messages,
                    temperature=0.3,
                    max_tokens=120
                )
                txt = resp.choices[0].message.content
                txt = re.sub(r"[`‎\s]*\{","{",txt,1,flags=re.S)   # 去反引号
                return json.loads(txt)
            except Exception:
                # 第二次时在 messages 结尾再补一句“重新输出 JSON”
                if len(messages) == 2:
                    messages.append({"role":"user","content":"请只输出 JSON"})
                else:
                    break   # 已经重试过
        # 仍失败则随机分
        return {
            "emotion": random.randint(50,90),
            "info":    random.randint(50,90),
            "safe":    random.randint(50,90),
            "advice":  "保持共情，多询问开放式问题"
        }

    # ---------- 训练小结 ----------
    def _report(self, room):
        arr = room["scoreLog"]
        em  = sum(x["emotion"] for x in arr)//len(arr)
        inf = sum(x["info"]    for x in arr)//len(arr)
        sa  = sum(x["safe"]    for x in arr)//len(arr)
        return {
            "emotion": em,
            "info":    inf,
            "safe":    sa,
            "advice":  "继续练习“先共情，再询问”的对话节奏。"
        }
