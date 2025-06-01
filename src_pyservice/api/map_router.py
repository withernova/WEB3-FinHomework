from flask import Flask, request, jsonify
import json
import numpy as np
from scipy.ndimage import gaussian_filter
from flask import Blueprint, request, jsonify, send_file
from services.map_service import MapHeatAIAssessor

ai_assessor = MapHeatAIAssessor()
map_bp = Blueprint("map", __name__)

def get_center(geometry):
    """根据GeoJSON geometry类型，返回其中心点(lng,lat)"""
    if geometry["type"] == "Point":
        return tuple(geometry["coordinates"])
    elif geometry["type"] == "LineString":
        coords = geometry["coordinates"]
        coords = np.array(coords)
        # 取中点
        idx = len(coords) // 2
        return tuple(coords[idx])
    elif geometry["type"] == "Polygon":
        # 合并所有ring的坐标
        all_coords = np.concatenate(geometry["coordinates"])
        lng = np.mean(all_coords[:, 0])
        lat = np.mean(all_coords[:, 1])
        return float(lng), float(lat)
    else:
        raise ValueError("Unsupported geometry type: {}".format(geometry["type"]))

@map_bp.route('/mapheat', methods=['POST'])
def mapheat():
    try:
        data = request.get_json(force=True) or {}
        markers = data.get("markers") or []
        if not markers:
            return jsonify(status="error", message="没有标注数据", heatmapPoints=[])

        # 1. 先收集所有marker_copy用于AI打分
        marker_copys = []
        centers = []
        for marker in markers:
            geometry = marker.get("geometry")
            if isinstance(geometry, str):
                geometry = json.loads(geometry)
            properties = marker.get("properties")
            if isinstance(properties, str):
                properties = json.loads(properties)
            marker_type = marker.get("markerType") or (properties or {}).get("markerType") or ""
            marker_copy = {
                "geometry": geometry,
                "properties": properties,
                "markerType": marker_type
            }
            marker_copys.append(marker_copy)
            lng, lat = get_center(geometry)
            centers.append((lng, lat))

        # 2. AI批量赋分（每个marker只打分一次）
        scores = ai_assessor.batch_score(marker_copys)  # 和marker_copys一一对应
        
        result_points = []
        for (lng, lat), value in zip(centers, scores):
            result_points.append({
                "lng": float(lng),
                "lat": float(lat),
                "value": float(value)   # value已是AI给的分数
            })

        print(result_points)
        return jsonify(status="success", heatmapPoints=result_points)
    except Exception as e:
        import traceback
        traceback.print_exc()
        return jsonify(status="error", message=str(e), heatmapPoints=[])