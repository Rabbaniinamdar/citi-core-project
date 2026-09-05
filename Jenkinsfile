pipeline {

    agent {
        label 'backend'
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Verify Repository') {
            steps {
                sh '''
                    echo "======================================"
                    echo "CitiCore Jenkins SCM Test"
                    echo "======================================"

                    echo "Git Commit:"
                    git rev-parse HEAD

                    echo ""
                    echo "Git Branch:"
                    git branch --show-current

                    echo ""
                    echo "Project Files:"
                    ls -la

                    echo ""
                    echo "Jenkinsfile:"
                    test -f Jenkinsfile && echo "Jenkinsfile found successfully"
                '''
            }
        }
    }

    post {
        success {
            echo 'CitiCore SCM pipeline test SUCCESS'
        }

        failure {
            echo 'CitiCore SCM pipeline test FAILED'
        }
    }
}