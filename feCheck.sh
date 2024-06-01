#!/bin/bash
cd /root/starrocks
>/root/starrocks/nohup.out
while true
do
    if ps -ef | grep -v grep | grep "build.sh" > /dev/null
    then
        echo "$(date) - Process is running." >> /tmp/check.log
    else
        echo "$(date) - Process not found. Checking logs..." >> /tmp/check.log
        if grep -q "BUILD SUCCESS" /root/starrocks/nohup.out
        then
	    echo "$(date) - BUILD SUCCESS found in log. No need to restart." >> /tmp/check.log
            sh /root/starrocks/notice.sh
	    /root/starrocks/output/fe/bin/stop_fe.sh
	    /root/starrocks/output/be/bin/stop_be.sh
            cp /root/starrocks/fe.conf /root/starrocks/output/fe/conf/
            cp /root/starrocks/be.conf /root/starrocks/output/be/conf/
	    mkdir -p /data/service/starrocks/fe/
	    mkdir -p /data/service/starrocks/be/
	    echo "start fe" >> /tmp/check.log
            /root/starrocks/output/fe/bin/start_fe.sh --daemon --debug
	    echo "start be" >> /tmp/check.log
            /root/starrocks/output/be/bin/start_be.sh --daemon
            break
        else
            echo "$(date) - BUILD SUCCESS not found in log. Restarting..." >> /tmp/check.log
            nohup ./build.sh --fe --clean &
        fi
    fi
    sleep 60
done
