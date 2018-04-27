# VASecond
分离音频轨和视频轨，分离视频（没有声音），分离音频（只有声音）

添加音频轨的MediaFormat到MediaMuxer时报错，可能是因为音频编码格式不合适，需要将现在的音频编码格式转化为PCM，然后再将PCM转为ACC
