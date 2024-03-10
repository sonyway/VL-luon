import flask
from flask import Flask, request, send_file, jsonify
import numpy as np
import io
import base64
import cv2
import tensorflow as tf
import utils
from utils import resize_img, _load_model_bbs, _load_model_lmks, overlay_transparent, angle_between
from tensorflow.keras.models import load_model
from tensorflow.keras.preprocessing import image
from PIL import Image
from io import BytesIO

global img
img = None
global glasses
glasses = cv2.imread('glasses.png', cv2.IMREAD_UNCHANGED)

app = Flask(__name__)

@app.route("/", methods=["GET"])
def hello_world():
    return "Hello World"

@app.route("/upload", methods=["POST"])
def upload_image():
    global img
    imagefile = request.files.get('image')

    # Read image using OpenCV
    img = cv2.imdecode(np.fromstring(imagefile.read(), np.uint8), cv2.IMREAD_UNCHANGED) 
    _, img_encoded = cv2.imencode('.jpeg', img)
    pil_image = Image.fromarray(cv2.cvtColor(img, cv2.COLOR_BGR2RGB))
    image_bytes = BytesIO()
    pil_image.save(image_bytes, format='JPEG')
    image_bytes.seek(0)
    
    return send_file(image_bytes, mimetype='image/jpeg')

@app.route("/predict", methods=["POST"])
def get_predictions():
    global img
    global glasses

    ori_img = img.copy()
    result_img = img.copy()

    img_size = 224
    if img is None:
        return jsonify({"error": "No image uploaded."}), 400
    
    # Load the prediction models
    model_bbs = utils._load_model_bbs()
    model_lmks = utils._load_model_lmks()

    # Perform prediction
    img, ratio, top, left = utils.resize_img(img)

    inputs = (img.astype('float32') / 255).reshape((1, img_size, img_size, 3))
    pred_bb = model_bbs.predict(inputs)[0].reshape((-1, 2))

    # compute bounding box of original image
    ori_bb = ((pred_bb - np.array([left, top])) / ratio).astype(int)

    # compute lazy bounding box for detecting landmarks
    center = np.mean(ori_bb, axis=0)
    face_size = max(np.abs(ori_bb[1] - ori_bb[0]))
    new_bb = np.array([
        center - face_size * 0.6,
        center + face_size * 0.6
    ]).astype(int)
    new_bb = np.clip(new_bb, 0, 99999)

    # predict landmarks
    face_img = ori_img[new_bb[0][1]:new_bb[1][1], new_bb[0][0]:new_bb[1][0]]
    face_img, face_ratio, face_top, face_left = resize_img(face_img)

    face_inputs = (face_img.astype('float32') / 255).reshape((1, img_size, img_size, 3))

    pred_lmks = model_lmks.predict(face_inputs)[0].reshape((-1, 2))

    # compute landmark of original image
    new_lmks = ((pred_lmks - np.array([face_left, face_top])) / face_ratio).astype(int)
    ori_lmks = new_lmks + new_bb[0]

    
    # visualize
    cv2.rectangle(ori_img, pt1=tuple(ori_bb[0]), pt2=tuple(ori_bb[1]), color=(255, 255, 255), thickness=2)

    for i, l in enumerate(ori_lmks):
        cv2.putText(ori_img, str(i), tuple(l), cv2.FONT_HERSHEY_SIMPLEX, 1, (255,255,255), 2, cv2.LINE_AA)
        cv2.circle(ori_img, center=tuple(l), radius=1, color=(255, 255, 255), thickness=2)
    # wearing glasses
    glasses_center = np.mean([ori_lmks[0], ori_lmks[1]], axis=0)
    glasses_size = np.linalg.norm(ori_lmks[0] - ori_lmks[1]) * 2
    
    angle = -angle_between(ori_lmks[0], ori_lmks[1])
    M = cv2.getRotationMatrix2D((glasses.shape[1] / 2, glasses.shape[0] / 2), angle, 1)
    rotated_glasses = cv2.warpAffine(glasses, M, (glasses.shape[1],glasses.shape[0]))

    result_img = overlay_transparent(result_img, rotated_glasses, glasses_center[0], 
                                         glasses_center[1], overlay_size=(int(glasses_size), 
                                                                          int(glasses.shape[0] * glasses_size / glasses.shape[1])))
        
    _, img_bytes = cv2.imencode('.jpg',result_img)
    img_stream = io.BytesIO(img_bytes)
    # Return the predictions as JSON response
    return send_file(img_stream , mimetype='image/jpeg')

if __name__ == "__main__":
    app.run(host='192.168.1.7', debug=True)