pipeline {

    agent {
        label 'backend'
    }

    parameters {

        choice(
            name: 'ROLLBACK_SERVICE',
            choices: [
                'NONE',
                'account',
                'transaction',
                'user',
                'auth',
                'notification',
                'gateway',
                'config',
                'eureka'
            ],
            description: 'Select service to rollback. Use NONE for normal deployment.'
        )

        string(
            name: 'ROLLBACK_SHA',
            defaultValue: '',
            description: 'Full Git SHA / ECR image tag to rollback to'
        )
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

        NOTIFICATION_ECS_SERVICE =
            'citicore-notification-service-service-0nnwrup9'

        GATEWAY_ECS_SERVICE =
            'citicore-apigateway-service'

        CONFIG_ECS_SERVICE =
            'citicore-config-server-service-rwdtvpj9'

        EUREKA_ECS_SERVICE =
            'citicore-eureka-server'
    }

    stages {

        // =========================================================
        // CHECKOUT
        // =========================================================

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


        // =========================================================
        // VALIDATE ROLLBACK
        // =========================================================

        stage('Validate Rollback') {

            steps {

                script {

                    if (params.ROLLBACK_SERVICE != 'NONE') {

                        if (!params.ROLLBACK_SHA?.trim()) {

                            error(
                                "ROLLBACK_SHA is required when " +
                                "ROLLBACK_SERVICE is selected."
                            )
                        }

                        if (!(params.ROLLBACK_SHA ==~ /^[0-9a-fA-F]{40}$/)) {

                            error(
                                "ROLLBACK_SHA must be a full " +
                                "40-character Git SHA."
                            )
                        }

                        echo "=========================================="
                        echo "ROLLBACK REQUEST"
                        echo "=========================================="
                        echo "Service : ${params.ROLLBACK_SERVICE}"
                        echo "SHA     : ${params.ROLLBACK_SHA}"
                        echo "=========================================="

                    } else {

                        echo "Normal deployment mode."
                    }
                }
            }
        }


        // =========================================================
        // DETECT CHANGED SERVICES
        // =========================================================

        stage('Detect Changed Services') {

            when {

                expression {

                    return params.ROLLBACK_SERVICE == 'NONE'
                }
            }

            steps {

                script {

                    def changedFiles = []

                    /*
                     * Jenkins Git changelog contains all commits
                     * associated with this build.
                     *
                     * This is more reliable than HEAD^ because
                     * one GitHub push can contain multiple commits.
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

                    changedFiles =
                        changedFiles.unique().sort()

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


                    // -------------------------------------------------
                    // Determine services
                    // -------------------------------------------------

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
                         * Shared kafka-events library.
                         *
                         * Changes can affect all consumers.
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

                        if (
                            file == 'pom.xml' ||
                            file.startsWith('citicore-platform/')
                        ) {

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
                         * Jenkinsfile / README / documentation
                         * intentionally do not trigger deployment.
                         */
                    }

                    services =
                        services.unique().sort()

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
                     * Store selected services.
                     */

                    env.CHANGED_SERVICES =
                        services.join(',')

                    echo "=========================================="

                    echo "CHANGED_SERVICES=" +
                        "${env.CHANGED_SERVICES}"

                    echo "=========================================="
                }
            }
        }


        // =========================================================
        // PREPARE ROLLBACK
        // =========================================================

        stage('Prepare Rollback') {

            when {

                expression {

                    return params.ROLLBACK_SERVICE != 'NONE'
                }
            }

            steps {

                script {

                    env.CHANGED_SERVICES =
                        params.ROLLBACK_SERVICE

                    env.ROLLBACK_MODE = 'true'

                    env.GIT_SHA =
                        params.ROLLBACK_SHA

                    echo "=========================================="
                    echo "ROLLBACK PREPARED"
                    echo "=========================================="

                    echo "Service : ${params.ROLLBACK_SERVICE}"

                    echo "SHA     : ${params.ROLLBACK_SHA}"

                    echo "=========================================="
                }
            }
        }


        // =========================================================
        // BUILD SERVICES
        // =========================================================

        stage('Build Services') {

            when {

                expression {

                    return (
                        params.ROLLBACK_SERVICE == 'NONE' &&
                        env.CHANGED_SERVICES?.trim()
                    )
                }
            }

            steps {

                script {

                    def services =
                        env.CHANGED_SERVICES
                            .split(',')
                            .findAll { it?.trim() }
                            .unique()


                    /*
                     * Build shared kafka-events first.
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

                                mvn -pl kafka-events -am \
                                    clean install \
                                    -DskipTests
                            '''
                        }
                    }


                    services.each { service ->

                        stage("Build ${service}") {

                            switch (service) {

                                case 'account':

                                    sh '''
                                        mvn -pl account-service -am \
                                            clean package \
                                            -DskipTests
                                    '''

                                    break


                                case 'transaction':

                                    sh '''
                                        mvn -pl transaction-service -am \
                                            clean package \
                                            -DskipTests
                                    '''

                                    break


                                case 'user':

                                    dir('user-service') {

                                        sh '''
                                            mvn clean package \
                                                -DskipTests
                                        '''
                                    }

                                    break


                                case 'auth':

                                    dir('auth-service') {

                                        sh '''
                                            mvn clean package \
                                                -DskipTests
                                        '''
                                    }

                                    break


                                case 'notification':

                                    dir('notification-service') {

                                        sh '''
                                            mvn clean package \
                                                -DskipTests
                                        '''
                                    }

                                    break


                                case 'gateway':

                                    dir('apigateway-service') {

                                        sh '''
                                            mvn clean package \
                                                -DskipTests
                                        '''
                                    }

                                    break


                                case 'config':

                                    dir('config-service') {

                                        sh '''
                                            mvn clean package \
                                                -DskipTests
                                        '''
                                    }

                                    break


                                case 'eureka':

                                    dir('eureka-server') {

                                        sh '''
                                            mvn clean package \
                                                -DskipTests
                                        '''
                                    }

                                    break


                                default:

                                    error(
                                        "Unknown service: ${service}"
                                    )
                            }
                        }
                    }
                }
            }
        }


        // =========================================================
        // BUILD DOCKER IMAGES
        // =========================================================

        stage('Build Docker Images') {

            when {

                expression {

                    return (
                        params.ROLLBACK_SERVICE == 'NONE' &&
                        env.CHANGED_SERVICES?.trim()
                    )
                }
            }

            steps {

                script {

                    def services =
                        env.CHANGED_SERVICES
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

                                directory =
                                    'notification-service'

                                repository =
                                    'notification-service'

                                break


                            case 'gateway':

                                directory =
                                    'apigateway-service'

                                repository =
                                    'apigateway-service'

                                break


                            case 'config':

                                directory =
                                    'config-service'

                                repository =
                                    'config-service'

                                break


                            case 'eureka':

                                directory =
                                    'eureka-server'

                                repository =
                                    'eureka-server'

                                break


                            default:

                                error(
                                    "Unknown service: ${service}"
                                )
                        }


                        stage("Docker ${service}") {

                            dir(directory) {

                                sh """

                                    echo "Building Docker image for ${service}"

                                    docker build \\
                                        -t ${ECR_REGISTRY}/citicore/${repository}:${GIT_SHA} \\
                                        .

                                """

                                echo (
                                    "Built ${repository}:${GIT_SHA}"
                                )
                            }
                        }
                    }
                }
            }
        }


        // =========================================================
        // LOGIN TO ECR
        // =========================================================

        stage('Login to ECR') {

            when {

                expression {

                    return (
                        params.ROLLBACK_SERVICE == 'NONE' &&
                        env.CHANGED_SERVICES?.trim()
                    )
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


        // =========================================================
        // PUSH IMAGES
        // =========================================================

        stage('Push Images to ECR') {

            when {

                expression {

                    return (
                        params.ROLLBACK_SERVICE == 'NONE' &&
                        env.CHANGED_SERVICES?.trim()
                    )
                }
            }

            steps {

                script {

                    def services =
                        env.CHANGED_SERVICES
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

                                repository =
                                    'transaction-service'

                                break


                            case 'user':

                                repository = 'user-service'

                                break


                            case 'auth':

                                repository = 'auth-service'

                                break


                            case 'notification':

                                repository =
                                    'notification-service'

                                break


                            case 'gateway':

                                repository =
                                    'apigateway-service'

                                break


                            case 'config':

                                repository =
                                    'config-service'

                                break


                            case 'eureka':

                                repository =
                                    'eureka-server'

                                break


                            default:

                                error(
                                    "Unknown service: ${service}"
                                )
                        }


                        stage("Push ${service}") {

                            sh """

                                docker push \
                                    ${ECR_REGISTRY}/citicore/${repository}:${GIT_SHA}

                            """

                            echo (
                                "Pushed ${repository}:${GIT_SHA}"
                            )
                        }
                    }
                }
            }
        }


        // =========================================================
        // DEPLOY TO ECS
        // =========================================================

        stage('Deploy to ECS') {

            when {

                expression {

                    return env.CHANGED_SERVICES?.trim()
                }
            }

            steps {

                script {

                    def services =
                        env.CHANGED_SERVICES
                            .split(',')
                            .findAll { it?.trim() }
                            .unique()


                    services.each { service ->

                        def ecsService
                        def repository


                        switch (service) {

                            case 'account':

                                ecsService =
                                    env.ACCOUNT_ECS_SERVICE

                                repository =
                                    'account-service'

                                break


                            case 'transaction':

                                ecsService =
                                    env.TRANSACTION_ECS_SERVICE

                                repository =
                                    'transaction-service'

                                break


                            case 'user':

                                ecsService =
                                    env.USER_ECS_SERVICE

                                repository =
                                    'user-service'

                                break


                            case 'auth':

                                ecsService =
                                    env.AUTH_ECS_SERVICE

                                repository =
                                    'auth-service'

                                break


                            case 'notification':

                                ecsService =
                                    env.NOTIFICATION_ECS_SERVICE

                                repository =
                                    'notification-service'

                                break


                            case 'gateway':

                                ecsService =
                                    env.GATEWAY_ECS_SERVICE

                                repository =
                                    'apigateway-service'

                                break


                            case 'config':

                                ecsService =
                                    env.CONFIG_ECS_SERVICE

                                repository =
                                    'config-service'

                                break


                            case 'eureka':

                                ecsService =
                                    env.EUREKA_ECS_SERVICE

                                repository =
                                    'eureka-server'

                                break


                            default:

                                error(
                                    "Unknown service: ${service}"
                                )
                        }


                        // -------------------------------------------------
                        // Deploy individual service
                        // -------------------------------------------------

                        stage("Deploy ${service}") {

                            def rollbackMode =
                                params.ROLLBACK_SERVICE != 'NONE'


                            /*
                             * Pass Groovy values into the shell
                             * as environment variables.
                             *
                             * This avoids Groovy interpreting
                             * shell $ variables.
                             */

                            withEnv([

                                "DEPLOY_SERVICE=${service}",

                                "ECS_SERVICE=${ecsService}",

                                "ECR_REPOSITORY=${repository}",

                                "DEPLOY_ROLLBACK=${rollbackMode}"

                            ]) {

                                sh '''

                                    set -e


                                    echo "=========================================="

                                    echo "Deploying ${DEPLOY_SERVICE}"

                                    echo "ECS Service: ${ECS_SERVICE}"

                                    echo "ECR Repository: citicore/${ECR_REPOSITORY}"

                                    echo "Image Tag: ${GIT_SHA}"

                                    echo "=========================================="


                                    # --------------------------------------
                                    # Deployment mode
                                    # --------------------------------------

                                    if [ "${DEPLOY_ROLLBACK}" = "true" ]; then

                                        echo "MODE: ROLLBACK"

                                    else

                                        echo "MODE: NORMAL DEPLOYMENT"

                                    fi


                                    # --------------------------------------
                                    # Validate rollback image
                                    # --------------------------------------

                                    if [ "${DEPLOY_ROLLBACK}" = "true" ]; then

                                        echo "Checking rollback image exists in ECR..."


                                        aws ecr describe-images \
                                            --repository-name citicore/${ECR_REPOSITORY} \
                                            --image-ids imageTag=${GIT_SHA} \
                                            --region ${AWS_REGION} \
                                            --query 'imageDetails[0].imageDigest' \
                                            --output text


                                        echo "Rollback image exists in ECR."

                                    fi


                                    # --------------------------------------
                                    # Get current task definition
                                    # --------------------------------------

                                    echo "Getting current ECS task definition..."


                                    TASK_DEF=$(aws ecs describe-services \
                                        --cluster ${ECS_CLUSTER} \
                                        --services ${ECS_SERVICE} \
                                        --region ${AWS_REGION} \
                                        --query 'services[0].taskDefinition' \
                                        --output text)


                                    echo "Current task definition:"

                                    echo "${TASK_DEF}"


                                    # --------------------------------------
                                    # Download task definition
                                    # --------------------------------------

                                    echo "Downloading task definition..."


                                    aws ecs describe-task-definition \
                                        --task-definition "${TASK_DEF}" \
                                        --region ${AWS_REGION} \
                                        --query 'taskDefinition' \
                                        --output json \
                                        > task-definition.json


                                    # --------------------------------------
                                    # Update application image
                                    # --------------------------------------

                                    echo "Updating application container image..."


                                    export DEPLOY_IMAGE="${ECR_REGISTRY}/citicore/${ECR_REPOSITORY}:${GIT_SHA}"


                                    python3 <<'PY'

import json
import os


path = "task-definition.json"


with open(path) as f:

    data = json.load(f)


repository = (
    os.environ["ECR_REGISTRY"]
    + "/citicore/"
    + os.environ["ECR_REPOSITORY"]
)


new_image = os.environ["DEPLOY_IMAGE"]


containers = data.get(
    "containerDefinitions",
    []
)


updated = False


for container in containers:

    image = container.get(
        "image",
        ""
    )


    if repository in image:

        print(
            "Updating container:",
            container.get("name")
        )

        print(
            "Old image:",
            image
        )

        print(
            "New image:",
            new_image
        )


        container["image"] = new_image

        updated = True

        break


if not updated:

    raise RuntimeError(
        "Application container not found for repository: "
        + repository
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

    data.pop(
        field,
        None
    )


with open(path, "w") as f:

    json.dump(
        data,
        f,
        indent=2
    )


print(
    "Task definition JSON updated successfully."
)

PY


                                    # --------------------------------------
                                    # Register new task definition
                                    # --------------------------------------

                                    echo "Registering new task definition..."


                                    NEW_TASK_DEF=$(aws ecs register-task-definition \
                                        --cli-input-json file://task-definition.json \
                                        --region ${AWS_REGION} \
                                        --query 'taskDefinition.taskDefinitionArn' \
                                        --output text)


                                    echo "New task definition:"

                                    echo "${NEW_TASK_DEF}"


                                    # --------------------------------------
                                    # Update ECS service
                                    # --------------------------------------

                                    echo "Updating ECS service..."


                                    aws ecs update-service \
                                        --cluster ${ECS_CLUSTER} \
                                        --service ${ECS_SERVICE} \
                                        --task-definition "${NEW_TASK_DEF}" \
                                        --region ${AWS_REGION} \
                                        --query 'service.taskDefinition' \
                                        --output text


                                    echo "=========================================="

                                    echo "ECS deployment started."

                                    echo "Waiting for ECS deployment to become healthy..."

                                    echo "=========================================="


                                    # --------------------------------------
                                    # WAIT FOR ECS DEPLOYMENT
                                    # --------------------------------------

                                    if aws ecs wait services-stable \
                                        --cluster ${ECS_CLUSTER} \
                                        --services ${ECS_SERVICE} \
                                        --region ${AWS_REGION}; then


                                        echo "=========================================="

                                        echo "ECS DEPLOYMENT SUCCESSFUL"

                                        echo "Service: ${ECS_SERVICE}"

                                        echo "Task Definition: ${NEW_TASK_DEF}"

                                        echo "=========================================="


                                    else


                                        echo "=========================================="

                                        echo "ECS DEPLOYMENT FAILED"

                                        echo "Service: ${ECS_SERVICE}"

                                        echo "=========================================="


                                        echo "Checking ECS deployment status..."


                                        aws ecs describe-services \
                                            --cluster ${ECS_CLUSTER} \
                                            --services ${ECS_SERVICE} \
                                            --region ${AWS_REGION} \
                                            --query 'services[0].deployments[].{Status:status,State:rolloutState,Reason:rolloutStateReason,TaskDefinition:taskDefinition,Desired:desiredCount,Running:runningCount}' \
                                            --output table


                                        echo "Checking recent ECS service events..."


                                        aws ecs describe-services \
                                            --cluster ${ECS_CLUSTER} \
                                            --services ${ECS_SERVICE} \
                                            --region ${AWS_REGION} \
                                            --query 'services[0].events[0:10].[createdAt,message]' \
                                            --output table


                                        exit 1

                                    fi


                                    # --------------------------------------
                                    # Final task definition verification
                                    # --------------------------------------

                                    echo "Final ECS task definition verification..."


                                    ACTUAL_TASK_DEF=$(aws ecs describe-services \
                                        --cluster ${ECS_CLUSTER} \
                                        --services ${ECS_SERVICE} \
                                        --region ${AWS_REGION} \
                                        --query 'services[0].taskDefinition' \
                                        --output text)


                                    echo "Expected:"

                                    echo "${NEW_TASK_DEF}"


                                    echo "Actual:"

                                    echo "${ACTUAL_TASK_DEF}"


                                    if [ "${ACTUAL_TASK_DEF}" != "${NEW_TASK_DEF}" ]; then

                                        echo "ERROR: ECS task definition mismatch"

                                        exit 1

                                    fi


                                    echo "=========================================="

                                    echo "Deployment verification completed."

                                    echo "Service: ${ECS_SERVICE}"

                                    echo "Task Definition: ${NEW_TASK_DEF}"

                                    echo "=========================================="

                                '''
                            }
                        }
                    }
                }
            }
        }


        // =========================================================
        // RECORD DEPLOYMENT
        // =========================================================

        stage('Record Deployment') {

            when {

                expression {

                    return env.CHANGED_SERVICES?.trim()
                }
            }

            steps {

                script {

                    def services =
                        env.CHANGED_SERVICES
                            .split(',')
                            .collect { it.trim() }


                    sh '''
                        mkdir -p deployment-history
                    '''


                    services.each { service ->

                        sh """

                            touch deployment-history/${service}


                            if [ -s deployment-history/${service} ]; then

                                sed -i "1i${GIT_SHA}" \
                                    deployment-history/${service}

                            else

                                echo "${GIT_SHA}" \
                                    > deployment-history/${service}

                            fi


                            head -20 deployment-history/${service} \
                                > deployment-history/${service}.tmp


                            mv \
                                deployment-history/${service}.tmp \
                                deployment-history/${service}

                        """
                    }


                    echo "=========================================="

                    echo "DEPLOYMENT HISTORY"

                    echo "=========================================="


                    sh '''

                        for file in deployment-history/*; do

                            echo ""

                            echo "Service: $(basename "$file")"

                            nl -ba "$file"

                        done

                    '''
                }
            }
        }
    }


    // =============================================================
    // POST ACTIONS
    // =============================================================

    post {

        success {

            echo '''

==========================================
CitiCore CI/CD SUCCESS
==========================================

ECS deployment completed successfully.

'''

        }


        failure {

            echo '''

==========================================
CitiCore CI/CD FAILED
==========================================

Check the stage above for the failure.

If ECS deployment failed, verify:

1. ECS deployment rollout state
2. ALB target health
3. ECS service events
4. Application logs
5. Deployment circuit breaker status

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