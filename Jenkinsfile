pipeline {

    agent {
        label 'backend'
    }

    options {
        skipDefaultCheckout(true)
        disableConcurrentBuilds()
        timestamps()
    }

    environment {
        AWS_REGION = 'ap-south-1'
        ECS_CLUSTER = 'citicore-cluster'
        ECR_REGISTRY = '580655778303.dkr.ecr.ap-south-1.amazonaws.com'

        // ECS service mappings
        ACCOUNT_ECS_SERVICE = 'citicore-account-service'
        TRANSACTION_ECS_SERVICE = 'citicore-transaction-service'
        USER_ECS_SERVICE = 'citicore-user-service'
        AUTH_ECS_SERVICE = 'citicore-auth-service'
        NOTIFICATION_ECS_SERVICE = 'citicore-notification-service-service-0nnwrup9'
        GATEWAY_ECS_SERVICE = 'citicore-apigateway-service'
        CONFIG_ECS_SERVICE = 'citicore-config-server-service-rwdtvpj9'
        EUREKA_ECS_SERVICE = 'citicore-eureka-server'
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm

                script {
                    env.GIT_SHA = sh(
                        script: 'git rev-parse HEAD',
                        returnStdout: true
                    ).trim()

                    echo "Git Commit SHA: ${env.GIT_SHA}"
                }

                sh '''
                    echo "Current commit:"
                    git log -1 --oneline
                '''
            }
        }

        stage('Detect Changed Services') {
            steps {
                script {

                    def changedFiles = []

                    /*
                     * Jenkins Git changelog contains all commits associated
                     * with this build. This is more reliable than using
                     * HEAD^ because a single GitHub push may contain
                     * multiple commits.
                     */
                    currentBuild.changeSets.each { changeSet ->

                        changeSet.items.each { entry ->

                            echo "Commit detected: ${entry.commitId}"
                            echo "Commit message: ${entry.msg}"

                            entry.affectedFiles.each { file ->
                                changedFiles << file.path
                            }
                        }
                    }

                    changedFiles = changedFiles.unique().sort()

                    echo "=========================================="
                    echo "Changed Files"
                    echo "=========================================="

                    if (changedFiles.isEmpty()) {

                        echo "No changed files detected."

                    } else {

                        changedFiles.each { file ->
                            echo "  ${file}"
                        }
                    }

                    /*
                     * Determine which application services need deployment.
                     */
                    def services = []

                    changedFiles.each { file ->

                        if (file.startsWith('account-service/')) {
                            services << 'account'
                        }

                        if (file.startsWith('transaction-service/')) {
                            services << 'transaction'
                        }

                        if (file.startsWith('user-service/')) {
                            services << 'user'
                        }

                        if (file.startsWith('auth-service/')) {
                            services << 'auth'
                        }

                        if (file.startsWith('notification-service/')) {
                            services << 'notification'
                        }

                        if (file.startsWith('apigateway-service/')) {
                            services << 'gateway'
                        }

                        if (file.startsWith('config-service/')) {
                            services << 'config'
                        }

                        if (file.startsWith('eureka-server/')) {
                            services << 'eureka'
                        }

                        /*
                         * Shared Kafka event library.
                         *
                         * Any change here can affect services consuming
                         * kafka-events.
                         */
                        if (file.startsWith('kafka-events/')) {

                            services.addAll([
                                'account',
                                'transaction',
                                'user',
                                'auth',
                                'notification'
                            ])
                        }

                        /*
                         * Root Maven configuration can affect
                         * multiple modules.
                         */
                        if (file == 'pom.xml' ||
                            file.startsWith('citicore-platform/')) {

                            services.addAll([
                                'account',
                                'transaction',
                                'user',
                                'auth',
                                'notification',
                                'gateway',
                                'config',
                                'eureka'
                            ])
                        }

                        /*
                         * Jenkinsfile, README, documentation, etc.
                         * intentionally do not trigger deployment.
                         */
                    }

                    services = services.unique().sort()

                    echo "=========================================="
                    echo "Services Selected For Deployment"
                    echo "=========================================="

                    if (services.isEmpty()) {

                        echo "  None"

                    } else {

                        services.each { service ->
                            echo "  ${service}"
                        }
                    }

                    /*
                     * Store selected services for the remaining stages.
                     */
                    env.CHANGED_SERVICES = services.join(',')

                    echo "=========================================="
                    echo "CHANGED_SERVICES=${env.CHANGED_SERVICES}"
                    echo "=========================================="
                }
            }
        }
        stage('Build Services') {
            when {
                expression {
                    return env.CHANGED_SERVICES?.trim()
                }
            }

            steps {
                script {

                    def services = env.CHANGED_SERVICES
                        .split(',')
                        .findAll { it?.trim() }
                        .unique()

                    /*
                     * kafka-events must be installed first because
                     * several standalone services depend on it.
                     */
                    if (
                        services.contains('account') ||
                        services.contains('transaction') ||
                        services.contains('user') ||
                        services.contains('auth') ||
                        services.contains('notification')
                    ) {
                        stage('Build Shared kafka-events') {

                            sh '''
                                echo "Building shared kafka-events..."

                                mvn -pl kafka-events -am clean install -DskipTests
                            '''
                        }
                    }

                    services.each { service ->

                        stage("Build ${service}") {

                            switch (service) {

                                case 'account':
                                    sh '''
                                        mvn -pl account-service -am clean package -DskipTests
                                    '''
                                    break

                                case 'transaction':
                                    sh '''
                                        mvn -pl transaction-service -am clean package -DskipTests
                                    '''
                                    break

                                case 'user':
                                    dir('user-service') {
                                        sh '''
                                            mvn clean package -DskipTests
                                        '''
                                    }
                                    break

                                case 'auth':
                                    dir('auth-service') {
                                        sh '''
                                            mvn clean package -DskipTests
                                        '''
                                    }
                                    break

                                case 'notification':
                                    dir('notification-service') {
                                        sh '''
                                            mvn clean package -DskipTests
                                        '''
                                    }
                                    break

                                case 'gateway':
                                    dir('apigateway-service') {
                                        sh '''
                                            mvn clean package -DskipTests
                                        '''
                                    }
                                    break

                                case 'config':
                                    dir('config-service') {
                                        sh '''
                                            mvn clean package -DskipTests
                                        '''
                                    }
                                    break

                                case 'eureka':
                                    dir('eureka-server') {
                                        sh '''
                                            mvn clean package -DskipTests
                                        '''
                                    }
                                    break

                                default:
                                    error "Unknown service: ${service}"
                            }
                        }
                    }
                }
            }
        }

        stage('Build Docker Images') {
            when {
                expression {
                    return env.CHANGED_SERVICES?.trim()
                }
            }

            steps {
                script {

                    def services = env.CHANGED_SERVICES
                        .split(',')
                        .findAll { it?.trim() }
                        .unique()

                    services.each { service ->

                        def directory
                        def repository

                        switch (service) {

                            case 'account':
                                directory = 'account-service'
                                repository = 'account-service'
                                break

                            case 'transaction':
                                directory = 'transaction-service'
                                repository = 'transaction-service'
                                break

                            case 'user':
                                directory = 'user-service'
                                repository = 'user-service'
                                break

                            case 'auth':
                                directory = 'auth-service'
                                repository = 'auth-service'
                                break

                            case 'notification':
                                directory = 'notification-service'
                                repository = 'notification-service'
                                break

                            case 'gateway':
                                directory = 'apigateway-service'
                                repository = 'apigateway-service'
                                break

                            case 'config':
                                directory = 'config-service'
                                repository = 'config-service'
                                break

                            case 'eureka':
                                directory = 'eureka-server'
                                repository = 'eureka-server'
                                break

                            default:
                                error "Unknown service: ${service}"
                        }

                        stage("Docker ${service}") {

                            dir(directory) {

                            sh """
                                        echo "Building Docker image for ${service}"

                                        docker build \
                                            -t ${ECR_REGISTRY}/citicore/${repository}:${GIT_SHA} \
                                            .
                            """
                            echo "Built ${repository}:${GIT_SHA}"
                            }
                        }
                    }
                }
            }
        }

        stage('Login to ECR') {
            when {
                expression {
                    return env.CHANGED_SERVICES?.trim()
                }
            }

            steps {
                sh '''
                    aws ecr get-login-password \
                      --region ${AWS_REGION} \
                    | docker login \
                      --username AWS \
                      --password-stdin ${ECR_REGISTRY}
                '''
            }
        }

        stage('Push Images to ECR') {
            when {
                expression {
                    return env.CHANGED_SERVICES?.trim()
                }
            }

            steps {
                script {

                    def services = env.CHANGED_SERVICES
                        .split(',')
                        .findAll { it?.trim() }
                        .unique()

                    services.each { service ->

                        def repository

                        switch (service) {

                            case 'account':
                                repository = 'account-service'
                                break

                            case 'transaction':
                                repository = 'transaction-service'
                                break

                            case 'user':
                                repository = 'user-service'
                                break

                            case 'auth':
                                repository = 'auth-service'
                                break

                            case 'notification':
                                repository = 'notification-service'
                                break

                            case 'gateway':
                                repository = 'apigateway-service'
                                break

                            case 'config':
                                repository = 'config-service'
                                break

                            case 'eureka':
                                repository = 'eureka-server'
                                break

                            default:
                                error "Unknown service: ${service}"
                        }

                        stage("Push ${service}") {

                            sh """
                                docker push ${ECR_REGISTRY}/citicore/${repository}:${GIT_SHA}
                            """
                            echo "Pushed ${repository}:${GIT_SHA}"
                        }
                    }
                }
            }
        }

        stage('Deploy to ECS') {
            when {
                expression {
                    return env.CHANGED_SERVICES?.trim()
                }
            }

            steps {
                script {

                    def services = env.CHANGED_SERVICES
                        .split(',')
                        .findAll { it?.trim() }
                        .unique()

                    services.each { service ->

                        def ecsService
                        def repository

                        switch (service) {

                            case 'account':
                                ecsService = env.ACCOUNT_ECS_SERVICE
                                repository = 'account-service'
                                break

                            case 'transaction':
                                ecsService = env.TRANSACTION_ECS_SERVICE
                                repository = 'transaction-service'
                                break

                            case 'user':
                                ecsService = env.USER_ECS_SERVICE
                                repository = 'user-service'
                                break

                            case 'auth':
                                ecsService = env.AUTH_ECS_SERVICE
                                repository = 'auth-service'
                                break

                            case 'notification':
                                ecsService = env.NOTIFICATION_ECS_SERVICE
                                repository = 'notification-service'
                                break

                            case 'gateway':
                                ecsService = env.GATEWAY_ECS_SERVICE
                                repository = 'apigateway-service'
                                break

                            case 'config':
                                ecsService = env.CONFIG_ECS_SERVICE
                                repository = 'config-service'
                                break

                            case 'eureka':
                                ecsService = env.EUREKA_ECS_SERVICE
                                repository = 'eureka-server'
                                break

                            default:
                                error "Unknown service: ${service}"
                        }

                        stage("Deploy ${service}") {

                            sh """
                                set -e

                                echo "=========================================="
                                echo "Deploying ${service}"
                                echo "ECS Service: ${ecsService}"
                                echo "ECR Repository: citicore/${repository}"
                                echo "Image Tag: ${GIT_SHA}"
                                echo "=========================================="

                                echo "Getting current ECS task definition..."

                                TASK_DEF=\$(aws ecs describe-services \
                                  --cluster ${ECS_CLUSTER} \
                                  --services ${ecsService} \
                                  --region ${AWS_REGION} \
                                  --query 'services[0].taskDefinition' \
                                  --output text)

                                echo "Current task definition:"
                                echo "\${TASK_DEF}"

                                echo "Downloading task definition..."

                                aws ecs describe-task-definition \
                                  --task-definition "\${TASK_DEF}" \
                                  --region ${AWS_REGION} \
                                  --query 'taskDefinition' \
                                  --output json > task-definition.json

                                echo "Updating application container image..."

                                python3 <<'PY'
import json

path = "task-definition.json"

with open(path) as f:
    data = json.load(f)

repository = "580655778303.dkr.ecr.ap-south-1.amazonaws.com/citicore/${repository}"
new_image = repository + ":${GIT_SHA}"

containers = data.get("containerDefinitions", [])

updated = False

for container in containers:
    image = container.get("image", "")

    if repository in image:
        print("Updating container:", container.get("name"))
        print("Old image:", image)
        print("New image:", new_image)

        container["image"] = new_image
        updated = True
        break

if not updated:
    raise RuntimeError(
        "Application container not found for repository: " + repository
    )

# Remove ECS response-only fields.
for field in [
    "taskDefinitionArn",
    "revision",
    "status",
    "requiresAttributes",
    "compatibilities",
    "registeredAt",
    "registeredBy"
]:
    data.pop(field, None)

with open(path, "w") as f:
    json.dump(data, f, indent=2)

print("Task definition JSON updated successfully.")
PY

                                echo "Registering new task definition..."

                                NEW_TASK_DEF=\$(aws ecs register-task-definition \
                                  --cli-input-json file://task-definition.json \
                                  --region ${AWS_REGION} \
                                  --query 'taskDefinition.taskDefinitionArn' \
                                  --output text)

                                echo "New task definition:"
                                echo "\${NEW_TASK_DEF}"

                                echo "Updating ECS service..."

                                aws ecs update-service \
                                  --cluster ${ECS_CLUSTER} \
                                  --service ${ecsService} \
                                  --task-definition "\${NEW_TASK_DEF}" \
                                  --region ${AWS_REGION} \
                                  --query 'service.taskDefinition' \
                                  --output text

                                echo "Waiting briefly for ECS to accept deployment..."

                                sleep 10

                                echo "Verifying ECS service..."

                                ACTUAL_TASK_DEF=\$(aws ecs describe-services \
                                  --cluster ${ECS_CLUSTER} \
                                  --services ${ecsService} \
                                  --region ${AWS_REGION} \
                                  --query 'services[0].taskDefinition' \
                                  --output text)

                                echo "Expected:"
                                echo "\${NEW_TASK_DEF}"

                                echo "Actual:"
                                echo "\${ACTUAL_TASK_DEF}"

                                if [ "\${ACTUAL_TASK_DEF}" != "\${NEW_TASK_DEF}" ]; then
                                    echo "ERROR: ECS task definition mismatch"
                                    exit 1
                                fi

                                echo "=========================================="
                                echo "ECS deployment accepted successfully"
                                echo "Service: ${ecsService}"
                                echo "Task Definition: \${NEW_TASK_DEF}"
                                echo "=========================================="
                            """
                        }
                    }
                }
            }
        }
    }

    post {

        success {
            echo '''
==========================================
CitiCore CI/CD SUCCESS
==========================================
'''
        }

        failure {
            echo '''
==========================================
CitiCore CI/CD FAILED
==========================================
Check the stage above for the failure.
'''
        }

        always {
            sh '''
                echo "Cleaning workspace..."

                rm -f task-definition.json || true

                docker logout ${ECR_REGISTRY} || true
            '''
        }
    }
}