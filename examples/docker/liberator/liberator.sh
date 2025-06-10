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

    # This is to ensure that we have at least 2 services and Liberator doesnt fall back to its default service behaviour.
    # It can be removed.
    add-peer
        remote-name               dummy
        remote-label              dummy
        remote-type               active
        local-type                active
        local-label               dummy
    end-peer

    add-data-service
            service-name        dummy

            add-source-group
            add-priority
                remote-label          dummy
            end-priority
            end-source-group
    end-data-service
    ' >> DeploymentFramework/global_config/overrides/servers/Liberator/etc/rttpd.conf

if [[ "${DISCOVERY_HOST}" == "null" ]]; then
  echo "DISCOVERY_HOST not set, setting datasrc-local-label to '$LOCAL_LABEL'"
  echo $'
      datasrc-local-label           ${ENV:LOCAL_LABEL}
      ' >> DeploymentFramework/global_config/overrides/servers/Liberator/etc/rttpd.conf
else
  echo "Connecting to Discovery on $DISCOVERY_HOST, setting datasrc-local-label to '$LOCAL_LABEL-$HOSTNAME'"
  DeploymentFramework/dfw deactivate LiberatorJMX
  DeploymentFramework/dfw activate LiberatorSockmon
  DeploymentFramework/dfw activate LiberatorDiscovery
  echo $'
      datasrc-local-label            ${ENV:LOCAL_LABEL}-${ENV:HOSTNAME}
      datasrc-interface              ${ENV:HOSTNAME}
      ' >> DeploymentFramework/global_config/overrides/servers/Liberator/etc/rttpd.conf
  echo $'
      define DISCOVERY_ADDR                                     ${ENV:DISCOVERY_HOST}
      define DISCOVERY_CLUSTER_NAME                             caplin
      ' >> DeploymentFramework/global_config/environment.conf
fi

if [ "$SSL_ENABLED" == "true" ];
then
    echo $'
        https-port               8080
        ' >> DeploymentFramework/global_config/overrides/servers/Liberator/etc/rttpd.conf
    DeploymentFramework/dfw activate HTTPS;
else
    echo $'
        http-port                8080
        ' >> DeploymentFramework/global_config/overrides/servers/Liberator/etc/rttpd.conf
    DeploymentFramework/dfw deactivate HTTPS;
fi

DeploymentFramework/dfw dump > /dev/null

echo '
*****Active config*****'
grep -v '^#' /app/DeploymentFramework/global_config/dump/Liberator/rttpd.conf
echo '*****End active config*****
'

DeploymentFramework/dfw start-fg Liberator
