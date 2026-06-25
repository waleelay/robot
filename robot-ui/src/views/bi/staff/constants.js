export const robots = [
    {
        "robotId": "robot-001",
        "clientId": "robot-media-client",
        "name": "R1轮式机器人",
        "type": "轮式机器人",
        "status": "online",
        "lastHeartbeatAt": "2026-05-25T03:39:24.879371Z",
        "cameras": [
            {
                "cameraId": "camera01",
                "deviceId": "camera01",
                "name": "前向双光云台",
                "groupType": "dual_gimbal",
                "quality": "sub"
            },
            {
                "cameraId": "camera02",
                "deviceId": "camera02",
                "name": "后向广角相机",
                "groupType": "body",
                "quality": "sub"
            },
            {
                "cameraId": "camera03",
                "deviceId": "camera03",
                "name": "机械臂腕部相机",
                "groupType": "arm",
                "quality": "sub"
            }
        ]
    },
    {
        "robotId": "robot-002",
        "clientId": "robot-media-client-robot-002",
        "name": "G1四足机器人",
        "type": "四足机器人",
        "status": "online",
        "lastHeartbeatAt": "2026-05-25T03:39:29.404898Z",
        "cameras": [
            {
                "cameraId": "camera04",
                "deviceId": "camera04",
                "name": "头部双光云台",
                "groupType": "dual_gimbal",
                "quality": "sub"
            },
            {
                "cameraId": "camera05",
                "deviceId": "camera05",
                "name": "腹部导航相机",
                "groupType": "body",
                "quality": "sub"
            },
            {
                "cameraId": "camera06",
                "deviceId": "camera06",
                "name": "尾部避障相机",
                "groupType": "body",
                "quality": "sub"
            }
        ]
    }
]
