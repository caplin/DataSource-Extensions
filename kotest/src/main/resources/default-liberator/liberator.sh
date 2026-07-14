#!/bin/bash

cd $(dirname "$0") || exit

FILE=DeploymentFramework/global_config/overrides/servers/Liberator/etc/rttpd.conf
ORIGINAL="$FILE.orig"
if [ ! -f "$ORIGINAL" ]; then
    mv $FILE $ORIGINAL
fi
cp $ORIGINAL $FILE

FILE=DeploymentFramework/global_config/environment.conf
ORIGINAL="$FILE.orig"
if [ ! -f "$ORIGINAL" ]; then
    mv $FILE $ORIGINAL
fi
cp $ORIGINAL $FILE

echo $'
    datasrc-pkt-log                /dev/null
    datasrc-ws-port                19000
    disable-default-data-service   TRUE
    ' >> DeploymentFramework/global_config/overrides/servers/Liberator/etc/rttpd.conf

echo $'
    datasrc-local-label           ${ENV:LOCAL_LABEL}
    ' >> DeploymentFramework/global_config/overrides/servers/Liberator/etc/rttpd.conf

echo $'
    http-port                18080
        ' >> DeploymentFramework/global_config/overrides/servers/Liberator/etc/rttpd.conf
    
if [ -f /app/adapter.conf ]; then
    cat /app/adapter.conf >> DeploymentFramework/global_config/overrides/servers/Liberator/etc/rttpd.conf
fi

if [ -f /app/beforeScript.sh ]; then
  source /app/beforeScript.sh
fi

DeploymentFramework/dfw dump > /dev/null

echo '
*****Active config*****'
grep -v '^#' /app/DeploymentFramework/global_config/dump/Liberator/rttpd.conf
echo '*****End active config*****
'

DeploymentFramework/dfw start-fg Liberator
