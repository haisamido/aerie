.DEFAULT_GOAL := help

.PHONY:

#export DOCKER_DEFAULT_PLATFORM=linux/arm64
export DOCKER_BUILDKIT=0

export SHELL=/bin/bash
export TZ=:UTC

# Kubernetes Defaults
K8S_CONTEXT=docker-desktop
K8S_CONTEXT_TODELETE=null
K8S_DEPLOYMENT=null

# Aerie defaults
ENVIRO=dev
AERIE_NAMESPACE=aerie-${ENVIRO}

AERIE_DEPLOYMENT_PATH=./deployment
AERIE_K8S_PATH=${AERIE_DEPLOYMENT_PATH}/kubernetes
AERIE_K8S_SECRETS_PATH=${AERIE_K8S_PATH}/secrets

directories:

installs:
	brew install kustomize
	brew install txn2/tap/kubefwd

aerie-docker-up: | aerie-docker-down ## aerie up via docker
	@echo source: https://nasa-ammos.github.io/aerie-docs/introduction/#fast-track
	cd ${AERIE_DEPLOYMENT_PATH} && \
	source .env && \
	docker compose up 

aerie-docker-down: ## aerie up via docker
	cd ${AERIE_DEPLOYMENT_PATH} && \
	docker compose down

aerie-kubernetes-up: ## aerie up via kubernetes
	@echo && echo "[INFO] Attempting to create namespace k8s:context:[${K8S_CONTEXT}]:namespace:[${AERIE_NAMESPACE}]" && \
	make kubectl-use-context K8S_CONTEXT=${K8S_CONTEXT} && \
	make kubectl-delete-namespace-${AERIE_NAMESPACE} || true && \
	make kubectl-create-namespace-${AERIE_NAMESPACE} && \
	cd ${AERIE_K8S_PATH} && \
	kubectl apply -f ./secrets/ -n ${AERIE_NAMESPACE} && \
	kubectl apply -k ./ -n ${AERIE_NAMESPACE}

# ./docker-entrypoint-initdb.d/

aerie-postgres-port-forward:
	kubectl --context "${K8S_CONTEXT}" --namespace "aerie-dev" port-forward service/postgres 5432:5432 &

aerie-db:
	source ./.env && \
	cd ${AERIE_DEPLOYMENT_PATH} && \
	  cd ./deployment/postgres-init-db && ./init-aerie.sh

#------------------------------------------------------------------------------
# kubectl targets
#------------------------------------------------------------------------------
kubectl-get-contexts: ## kubectl get contexts
	@echo && kubectl config get-contexts

kubectl-use-context: ## kubectl use-context K8S_CONTEXT=<default=docker-desktop>
	@echo && echo "[INFO] Attempting to use-context k8s:context:[${K8S_CONTEXT}]"
	@kubectl config use-context ${K8S_CONTEXT}

kubectl-delete-context: ## kubectl config delete-context K8S_CONTEXT_TODELETE=<default=null>
	@echo && echo "[INFO] Attempting to delete context k8s:context:[${K8S_CONTEXT_TODELETE}]"
	@kubectl config delete-context ${K8S_CONTEXT_TODELETE} || true

kubectl-cluster-info: ## kubectl cluster-info
	@echo && kubectl cluster-info

kubectl-get-nodes: kubectl-use-context ## kubectl get nodes
	@echo && kubectl get nodes

kubectl-get-namespaces: kubectl-use-context ## kubectl get namespaces
	@echo && kubectl get namespaces

kubectl-get-secrets-%: ## kubectl get secrets -n %
	@echo && kubectl get secrets -n $*

kubectl-get-pvc-%: ## kubectl get pvc --namespace %
	@echo && kubectl get pvc --namespace $*

kubectl-get-pods-%: ## kubectl get pods --namespace %
	kubectl get pods -n $*

kubectl-create-namespace-%: ## kubectl create namespace %
	@echo && echo "[INFO] Attempting to create namespace k8s:context:[${K8S_CONTEXT}]:namespace:[$*]"
	kubectl create namespace $*

kubectl-delete-namespace-%: ## kubectl delete namespace %
	@echo && echo "[INFO] Attempting to delete namespace k8s:context:[${K8S_CONTEXT}]:namespace:[$*]"
	@kubectl delete namespace $*

kubectl-get-deployments: ## kubectl get deployment
	@make kubectl-use-context K8S_CONTEXT=${K8S_CONTEXT} && \
	kubectl get deployment

kubectl-delete-deployment-%: ## kubectl delete -n $* deployment ${K8S_DEPLOYMENT}
	@kubectl delete -n $* deployment ${K8S_DEPLOYMENT}

clean-kubernetes: ## delete k8s namespaces (of course NOT default)
	@make kubectl-delete-namespace-aerie-dev || true

clean-docker:

clean:
	

references:
	@echo https://github.com/txn2/kubefwd/blob/master/README.md
	@echo https://www.linkedin.com/pulse/running-postgresql-docker-container-kubernetes-persistent-pudi-n2xue/
	@echo https://nasa-ammos.github.io/aerie-docs/introduction/#fast-track
	@echo https://nasa-ammos.github.io/aerie-docs/planning/upload-mission-model/ 
	@echo https://fluxcd.io/flux/components/kustomize/kustomizations/
	@echo https://nasa-ammos.github.io/aerie-docs/introduction/#fast-track 
	@echo https://github.com/NASA-AMMOS/aerie/tree/develop/deployment
	@echo https://github.com/NASA-AMMOS/aerie/releases

print-%:
	@echo $*=$($*)

export MAKEFILE_LIST=Makefile

help:
	@printf "\033[37m%-30s\033[0m %s\n" "#----------------------------------------------------------------------------------"
	@printf "\033[37m%-30s\033[0m %s\n" "# Makefile targets                                                                 |"
	@printf "\033[37m%-30s\033[0m %s\n" "#----------------------------------------------------------------------------------"
	@printf "\033[37m%-30s\033[0m %s\n" "#-target-----------------------description-----------------------------------------"
	@grep -E '^[a-zA-Z_-].+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-30s\033[0m %s\n", $$1, $$2}'
