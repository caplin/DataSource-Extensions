FROM docker-release.caplin.com/platform/core:8.0.9 AS platform

USER root

RUN microdnf update -y && \
    microdnf install -y unzip wget ca-certificates && \
    microdnf upgrade -y && \
    DeploymentFramework/dfw deactivate PermissioningService && \
    DeploymentFramework/dfw deactivate ChartingService && \
    DeploymentFramework/dfw deactivate AlertsService && \
    DeploymentFramework/dfw deactivate WatchlistService && \
    DeploymentFramework/dfw deactivate TransformerJMX && \
    DeploymentFramework/dfw deactivate BlotterExport && \
    DeploymentFramework/dfw deactivate PersistenceService && \
    DeploymentFramework/dfw deactivate DecisionSupportService && \
    DeploymentFramework/dfw deactivate RefinerService && \
    DeploymentFramework/dfw deactivate PersistenceServiceClient && \
    DeploymentFramework/dfw deactivate TransformerToLiberatorConnection && \
    DeploymentFramework/dfw activate OpenPermissioning && \
    rm -rf /tmp/yum*

COPY liberator.sh liberator.sh

RUN chmod 755 liberator.sh

USER platform

ENV SSL_ENABLED=false
ENV LOCAL_LABEL=liberator
ENV DISCOVERY_HOST=null
VOLUME /app/DeploymentFramework/global_config/ssl
VOLUME /app/DeploymentFramework/global_config/licenses
EXPOSE 19000
EXPOSE 8080

CMD ["/app/liberator.sh"]
