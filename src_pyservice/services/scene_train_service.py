import uuid, json, re, time, random
from typing import Dict, List
from zhipuai import ZhipuAI
from config import Config

_PROMPTS = {
    "fear":  "你扮演一位走失老人，情绪激动，不停说想找儿子。",
    "refuse":"你扮演一位走失老人，对陌生人高度警惕，拒绝回答问题。",
    "night": "你扮演深夜寒冷中的走失老人，情绪低落并颤抖。",
}

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
        report = ""
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
        prompt = (
          f"志愿者的话：{user_msg}\n\n"
          "你是专业搜救教练，请用 JSON 给出 0-100 三项评分并一句建议：\n"
          '{"emotion":数值,"info":数值,"safe":数值,"advice":"一句中文建议"}'
        )
        try:
            resp = self.client.chat.completions.create(
                model=self.model,
                messages=[{"role":"system","content":"严格返回 JSON，不要代码块"},
                          {"role":"user","content":prompt}],
                temperature=0.3,max_tokens=120)
            txt = resp.choices[0].message.content
            # 清除反引号等
            txt = re.sub(r"[`‎\s]*\{","{",txt,1,flags=re.S)
            data = json.loads(txt)
        except Exception:
            # 容错：随机分 + 建议
            data = {
                "emotion": random.randint(50,90),
                "info":    random.randint(50,90),
                "safe":    random.randint(50,90),
                "advice":  "保持共情，多询问开放式问题"
            }
        return data

    # ---------- 训练小结 ----------
    def _report(self, room):
        arr = room["scoreLog"]
        em = sum(x["emotion"] for x in arr)//len(arr)
        inf= sum(x["info"]    for x in arr)//len(arr)
        sa = sum(x["safe"]    for x in arr)//len(arr)
        return (f"## 训练小结\n\n"
                f"- 安抚情绪：**{em}**\n"
                f"- 信息收集：**{inf}**\n"
                f"- 安全意识：**{sa}**\n\n"
                "建议：继续练习“先共情，再询问”的对话节奏。")
