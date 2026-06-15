export const menuData = {
    "msg": "操作成功",
    "code": 200,
    "data": [
        {
            "path": "/",
            "hidden": false,
            "component": "Layout",
            "children": [
                {
                    "name": "Home",
                    "path": "home",
                    "hidden": false,
                    "component": "home",
                    "meta": {
                        "title": "首页",
                        "icon": "shopping",
                        "noCache": false,
                        "link": null
                    }
                }
            ]
        },
        {
            "path": "/",
            "hidden": false,
            "component": "Layout",
            "children": [
                {
                    "name": "Map",
                    "path": "map",
                    "hidden": false,
                    "component": "map/index",
                    "meta": {
                        "title": "地图接入",
                        "icon": "search",
                        "noCache": false,
                        "link": null
                    }
                },
                {
                    "name": "mapAdd",
                    "path": "map/add",
                    "hidden": true,
                    "component": "map/add",
                    "meta": {
                        "title": "添加地图",
                        "noCache": false,
                        "link": null
                    }
                }
            ]
        },
        {
            "path": "/",
            "hidden": false,
            "component": "Layout",
            "children": [
                {
                    "name": "Equipment",
                    "path": "equipment",
                    "hidden": false,
                    "component": "equipment/index",
                    "meta": {
                        "title": "装备接入",
                        "icon": "redis-list",
                        "noCache": false,
                        "link": null
                    }
                }
            ]
        },
        {
            "path": "/",
            "hidden": true,
            "component": "Layout",
            "children": [
                {
                    "name": "Alg",
                    "path": "alg",
                    "hidden": false,
                    "component": "alg/index",
                    "meta": {
                        "title": "算法管理",
                        "icon": "button",
                        "noCache": false,
                        "link": null
                    }
                }
            ]
        },
        {
            "path": "/",
            "hidden": false,
            "component": "Layout",
            "children": [
                {
                    "name": "Remote",
                    "path": "remote",
                    "hidden": false,
                    "component": "remote/index",
                    "meta": {
                        "title": "远程控制",
                        "icon": "clipboard",
                        "noCache": false,
                        "link": null
                    }
                }
            ]
        },
        {
            "path": "/",
            "hidden": false,
            "component": "Layout",
            "children": [
                {
                    "name": "Scene",
                    "path": "scene",
                    "hidden": false,
                    "component": "scene/index",
                    "meta": {
                        "title": "场景注册",
                        "icon": "component",
                        "noCache": false,
                        "link": null
                    }
                }
            ]
        },
        {
            "path": "/",
            "hidden": false,
            "component": "Layout",
            "children": [
                {
                    "name": "Path",
                    "path": "path",
                    "hidden": false,
                    "component": "path/index",
                    "meta": {
                        "title": "路径规划",
                        "icon": "cascader",
                        "noCache": false,
                        "link": null
                    }
                },
                // {
                //     "name": "pathAdd",
                //     "path": "path/add",
                //     "hidden": true,
                //     "component": "path/add",
                //     "meta": {
                //         "title": "新增路径",
                //         "noCache": false,
                //         "link": null
                //     }
                // }
            ]
        },
        {
            "path": "/",
            "hidden": false,
            "component": "Layout",
            "children": [
                {
                    "name": "Dispatch",
                    "path": "dispatch",
                    "hidden": false,
                    "component": "dispatch/index",
                    "meta": {
                        "title": "调度任务",
                        "icon": "excel",
                        "noCache": false,
                        "link": null
                    }
                }
            ]
        },
        {
            "path": "/",
            "hidden": false,
            "component": "Layout",
            "children": [
                {
                    "name": "Execute-log",
                    "path": "execute-log",
                    "hidden": false,
                    "component": "execute-log/index",
                    "meta": {
                        "title": "执行记录",
                        "icon": "documentation",
                        "noCache": false,
                        "link": null
                    }
                }
            ]
        },
        {
            "path": "/",
            "hidden": false,
            "component": "Layout",
            "children": [
                {
                    "name": "Alarm",
                    "path": "alarm",
                    "hidden": false,
                    "component": "alarm/index",
                    "meta": {
                        "title": "告警信息",
                        "icon": "bug",
                        "noCache": false,
                        "link": null
                    }
                }
            ]
        },
        {
            "path": "/",
            "hidden": true,
            "component": "Layout",
            "children": [
                {
                    "name": "Data",
                    "path": "data",
                    "hidden": false,
                    "component": "data/index",
                    "meta": {
                        "title": "统计分析",
                        "icon": "chart",
                        "noCache": false,
                        "link": null
                    }
                }
            ]
        },
        {
            "name": "Robot",
            "path": "/robot",
            "hidden": false,
            "redirect": "noRedirect",
            "component": "Layout",
            "alwaysShow": true,
            "meta": {
                "title": "机器人管理",
                "icon": "bug",
                "noCache": false,
                "link": null
            },
            "children": [
                {
                    "name": "RobotMessage",
                    "path": "robotMessage",
                    "hidden": false,
                    "component": "robot/robotMessage/index",
                    "meta": {
                        "title": "机器人信息",
                        "icon": "logininfor",
                        "noCache": false,
                        "link": null
                    }
                },
                {
                    "name": "Point",
                    "path": "point",
                    "hidden": false,
                    "component": "robot/point/index",
                    "meta": {
                        "title": "点位信息",
                        "icon": "radio",
                        "noCache": false,
                        "link": null
                    }
                }
            ]
        },
        {
            "name": "Task",
            "path": "/task",
            "hidden": false,
            "redirect": "noRedirect",
            "component": "Layout",
            "alwaysShow": true,
            "meta": {
                "title": "任务管理",
                "icon": "tree-table",
                "noCache": false,
                "link": null
            },
            "children": [
                {
                    "name": "TaskRoute",
                    "path": "taskRoute",
                    "hidden": false,
                    "component": "task/taskRoute/index",
                    "meta": {
                        "title": "任务路径",
                        "icon": "cascader",
                        "noCache": false,
                        "link": null
                    }
                },
                {
                    "name": "SetTask",
                    "path": "setTask",
                    "hidden": false,
                    "component": "rollCall/setTask/index",
                    "meta": {
                        "title": "定时任务",
                        "icon": "guide",
                        "noCache": false,
                        "link": null
                    }
                }
            ]
        },
        {
            "name": "RollCall",
            "path": "/rollCall",
            "hidden": false,
            "redirect": "noRedirect",
            "component": "Layout",
            "alwaysShow": true,
            "meta": {
                "title": "点名系统",
                "icon": "peoples",
                "noCache": false,
                "link": null
            },
            "children": [
                {
                    "name": "People",
                    "path": "people",
                    "hidden": false,
                    "component": "rollCall/people/index",
                    "meta": {
                        "title": "点名人员信息",
                        "icon": "user",
                        "noCache": false,
                        "link": null
                    }
                },
                {
                    "name": "PointPeopel",
                    "path": "pointPeopel",
                    "hidden": false,
                    "component": "rollCall/pointPeople/index",
                    "meta": {
                        "title": "点位人员配置",
                        "icon": "link",
                        "noCache": false,
                        "link": null
                    }
                },
                {
                    "name": "Result",
                    "path": "result",
                    "hidden": false,
                    "component": "rollCall/result/index",
                    "meta": {
                        "title": "点名结果总览",
                        "icon": "druid",
                        "noCache": false,
                        "link": null
                    }
                }
            ]
        },
        {
            "name": "Cruise",
            "path": "/cruise",
            "hidden": false,
            "redirect": "noRedirect",
            "component": "Layout",
            "alwaysShow": true,
            "meta": {
                "title": "巡航系统",
                "icon": "drag",
                "noCache": false,
                "link": null
            },
            "children": [
                {
                    "name": "Cruise",
                    "path": "cruise",
                    "hidden": false,
                    "component": "cruise/cruise/index",
                    "meta": {
                        "title": "巡航结果记录",
                        "icon": "build",
                        "noCache": false,
                        "link": null
                    }
                },
                {
                    "name": "Error",
                    "path": "error",
                    "hidden": false,
                    "component": "cruise/error/index",
                    "meta": {
                        "title": "巡航异常记录",
                        "icon": "404",
                        "noCache": false,
                        "link": null
                    }
                }
            ]
        },
        {
            "name": "System",
            "path": "/system",
            "hidden": false,
            "redirect": "noRedirect",
            "component": "Layout",
            "alwaysShow": true,
            "meta": {
                "title": "系统管理",
                "icon": "system",
                "noCache": false,
                "link": null
            },
            "children": [
                {
                    "name": "User",
                    "path": "user",
                    "hidden": false,
                    "component": "system/user/index",
                    "meta": {
                        "title": "用户管理",
                        "icon": "user",
                        "noCache": false,
                        "link": null
                    }
                },
                {
                    "name": "Online",
                    "path": "online",
                    "hidden": false,
                    "component": "monitor/online/index",
                    "meta": {
                        "title": "在线用户",
                        "icon": "online",
                        "noCache": false,
                        "link": null
                    }
                },
                {
                    "name": "Menu",
                    "path": "menu",
                    "hidden": false,
                    "component": "system/menu/index",
                    "meta": {
                        "title": "菜单管理",
                        "icon": "tree-table",
                        "noCache": false,
                        "link": null
                    }
                },
                {
                    "name": "Server",
                    "path": "server",
                    "hidden": false,
                    "component": "monitor/server/index",
                    "meta": {
                        "title": "服务监控",
                        "icon": "server",
                        "noCache": false,
                        "link": null
                    }
                },
                {
                    "name": "Cache",
                    "path": "cache",
                    "hidden": false,
                    "component": "monitor/cache/index",
                    "meta": {
                        "title": "缓存监控",
                        "icon": "redis",
                        "noCache": false,
                        "link": null
                    }
                },
                {
                    "name": "Dict",
                    "path": "dict",
                    "hidden": false,
                    "component": "system/dict/index",
                    "meta": {
                        "title": "字典管理",
                        "icon": "dict",
                        "noCache": false,
                        "link": null
                    }
                },
                {
                    "name": "Config",
                    "path": "config",
                    "hidden": false,
                    "component": "system/config/index",
                    "meta": {
                        "title": "参数设置",
                        "icon": "edit",
                        "noCache": false,
                        "link": null
                    }
                }
            ]
        },
        {
            "name": "Tool",
            "path": "/tool",
            "hidden": false,
            "redirect": "noRedirect",
            "component": "Layout",
            "alwaysShow": true,
            "meta": {
                "title": "系统工具",
                "icon": "tool",
                "noCache": false,
                "link": null
            },
            "children": [
                {
                    "name": "Build",
                    "path": "build",
                    "hidden": false,
                    "component": "tool/build/index",
                    "meta": {
                        "title": "表单构建",
                        "icon": "build",
                        "noCache": false,
                        "link": null
                    }
                },
                {
                    "name": "Gen",
                    "path": "gen",
                    "hidden": false,
                    "component": "tool/gen/index",
                    "meta": {
                        "title": "代码生成",
                        "icon": "code",
                        "noCache": false,
                        "link": null
                    }
                },
                {
                    "name": "Swagger",
                    "path": "swagger",
                    "hidden": false,
                    "component": "tool/swagger/index",
                    "meta": {
                        "title": "系统接口",
                        "icon": "swagger",
                        "noCache": false,
                        "link": null
                    }
                }
            ]
        }
    ]
    }
