pipeline {

    agent {
        label 'backend'
    }

    options {
        timestamps()
        disableConcurrentBuilds()
        skipDefaultCheckout(true)
    }

    environment {
        AWS_REGION = 'ap-south-1'
        AWS_ACCOUNT_ID = '580655778303'
        ECS_CLUSTER = 'citicore-cluster'

        ECR_REGISTRY =
            "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
    }

    stages {

        stage('Checkout') {
            steps {

                echo '============================================'
                echo 'CHECKOUT'
                echo '============================================'

                checkout scm

                sh '''
                    echo "Commit:"
                    git rev-parse HEAD

                    echo ""
                    echo "Branch:"
                    git branch --show-current
                '''
            }
        }


        stage('Detect Changes') {
            steps {

                script {

                    echo '============================================'
                    echo 'DETECT CHANGES'
                    echo '============================================'


                    def currentCommit = sh(
                        script: 'git rev-parse HEAD',
                        returnStdout: true
                    ).trim()


                    def previousCommit = sh(
                        script: '''
                            git rev-parse HEAD^ 2>/dev/null ||
                            git rev-list --max-parents=0 HEAD
                        ''',
                        returnStdout: true
                    ).trim()


                    echo "Previous commit: ${previousCommit}"
                    echo "Current commit : ${currentCommit}"


                    def changedFiles = sh(
                        script: """
                            git diff --name-only \
                            ${previousCommit} \
                            ${currentCommit}
                        """,
                        returnStdout: true
                    ).trim()


                    echo ''
                    echo 'Changed files:'
                    echo '--------------------------------------------'
                    echo changedFiles
                    echo '--------------------------------------------'


                    def files = changedFiles ?
                        changedFiles.split('\\n') :
                        []


                    def services = []


                    if (files.any {
                        it.startsWith('account-service/')
                    }) {
                        services.add('account-service')
                    }


                    if (files.any {
                        it.startsWith('transaction-service/')
                    }) {
                        services.add('transaction-service')
                    }


                    if (files.any {
                        it.startsWith('user-service/')
                    }) {
                        services.add('user-service')
                    }


                    if (files.any {
                        it.startsWith('auth-service/')
                    }) {
                        services.add('auth-service')
                    }


                    if (files.any {
                        it.startsWith('notification-service/')
                    }) {
                        services.add('notification-service')
                    }


                    if (files.any {
                        it.startsWith('apigateway-service/')
                    }) {
                        services.add('apigateway-service')
                    }


                    if (files.any {
                        it.startsWith('config-service/')
                    }) {
                        services.add('config-service')
                    }


                    if (files.any {
                        it.startsWith('eureka-server/')
                    }) {
                        services.add('eureka-server')
                    }


                    /*
                     * Shared Kafka event library
                     */
                    if (files.any {
                        it.startsWith('kafka-events/')
                    }) {

                        echo ''
                        echo 'kafka-events changed.'
                        echo 'Adding dependent services.'

                        services.add('account-service')
                        services.add('transaction-service')
                        services.add('notification-service')
                    }


                    /*
                     * Root Maven POM
                     */
                    if (files.any {
                        it == 'pom.xml'
                    }) {

                        echo ''
                        echo 'Root pom.xml changed.'

                        services.add('account-service')
                        services.add('transaction-service')
                    }


                    services = services.unique()


                    env.CHANGED_SERVICES =
                        services.join(',')


                    echo ''
                    echo '============================================'
                    echo 'SERVICES TO DEPLOY'
                    echo '============================================'


                    if (services.isEmpty()) {

                        echo 'NONE'

                    } else {

                        services.each {
                            echo " -> ${it}"
                        }
                    }


                    echo '============================================'
                }
            }
        }


        stage('Test Result') {

            steps {

                script {

                    echo ''
                    echo '============================================'
                    echo 'CHANGE DETECTION RESULT'
                    echo '============================================'


                    if (env.CHANGED_SERVICES?.trim()) {

                        echo "Detected services:"
                        echo env.CHANGED_SERVICES

                    } else {

                        echo 'No deployable service changed.'
                    }


                    echo ''
                    echo 'DEPLOYMENT IS CURRENTLY DISABLED.'
                    echo 'This is a detection-only test.'
                    echo '============================================'
                }
            }
        }
    }


    post {

        success {

            echo ''
            echo '============================================'
            echo 'DETECTION TEST SUCCESS'
            echo '============================================'
        }


        failure {

            echo ''
            echo '============================================'
            echo 'DETECTION TEST FAILED'
            echo '============================================'
        }
    }
}