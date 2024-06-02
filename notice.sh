#!/bin/bash

# 钉钉机器人 Webhook 地址
url="https://oapi.dingtalk.com/robot/send?access_token=d0f4289303c2cbe272220076aa5b0fbceff45fab45701cdf4bda9ef47a699ec1"

# 通知标题
title="build success"

# 通知内容
content="Ubuntu 脚本已经构建完成，可以进行部署了。"

# 发送 HTTP POST 请求
curl $url \
-H 'Content-Type: application/json' \
-d "{
    \"msgtype\": \"text\",
    \"text\": {
        \"content\": \"$title\n$content\"
    }
}"
