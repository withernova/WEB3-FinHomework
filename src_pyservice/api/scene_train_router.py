from flask import Blueprint, request, jsonify
import uuid
from services.scene_train_service import SceneTrainService

scene_bp = Blueprint("scene_train_bp", __name__)
svc = SceneTrainService()

@scene_bp.route("/scene-train/chat", methods=["POST"])
def chat():
    data = request.get_json(force=True)
    sid  = data.get("session_id") or str(uuid.uuid4())
    cmd  = data.get("cmd")

    try:
        if cmd == "start":
            res = svc.start(sid, data.get("scene", "fear"))

        elif cmd == "talk":
            res = svc.talk(sid, data.get("content", ""))

        else:
            return jsonify(error="invalid cmd"), 400

        res["session_id"] = sid        # ← 放到统一出口
        return jsonify(res)

    except Exception as e:
        return jsonify(error=str(e)), 400
