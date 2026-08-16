// Jenkinsfile
//
// clickkart-cart-service's own copy of Jenkinsfile.template. Only SERVICE_NAME/SERVICE_PORT
// differ from the template; everything else is identical by design.

pipeline {
    agent any

    tools {
        maven 'maven-3.9'
        jdk 'temurin-21'
    }

    parameters {
        choice(name: 'DEPLOY_ENV', choices: ['none', 'dev', 'qa', 'prod'], description: 'Environment to deploy to after a successful build. "none" just builds/tests/scans.')
    }

    environment {
        SERVICE_NAME   = 'clickkart-cart-service'
        SERVICE_PORT   = '8091'
        REGISTRY       = credentials('clickkart-registry-url')
        REGISTRY_CREDS = 'clickkart-registry-credentials'
        IMAGE_TAG      = "${env.BUILD_NUMBER}-${env.GIT_COMMIT?.take(7) ?: 'local'}"
        IMAGE          = "${REGISTRY}/${SERVICE_NAME}:${IMAGE_TAG}"
        TRIVY_SEVERITY = 'CRITICAL,HIGH'
    }

    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '30'))
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build') {
            steps {
                sh 'mvn -B -q clean compile'
            }
        }

        stage('Unit Test') {
            steps {
                sh 'mvn -B verify'
            }
            post {
                always {
                    junit testResults: 'target/surefire-reports/*.xml', allowEmptyResults: false
                    jacoco execPattern: 'target/jacoco.exec',
                           classPattern: 'target/classes',
                           sourcePattern: 'src/main/java',
                           exclusionPattern: '**/*MapperImpl.class,**/*Application.class'
                    archiveArtifacts artifacts: 'target/site/jacoco/**', allowEmptyArchive: true
                }
            }
        }

        stage('Dependency Scan (OWASP)') {
            steps {
                sh 'mvn -B org.owasp:dependency-check-maven:check'
            }
            post {
                always {
                    archiveArtifacts artifacts: 'target/dependency-check-report.*', allowEmptyArchive: true
                }
            }
        }

        stage('Docker Build') {
            steps {
                sh "docker build -t ${IMAGE} -t ${REGISTRY}/${SERVICE_NAME}:latest ."
            }
        }

        stage('Image Scan (Trivy)') {
            steps {
                sh """
                    trivy image \
                        --severity ${TRIVY_SEVERITY} \
                        --exit-code 1 \
                        --format table \
                        --ignore-unfixed \
                        ${IMAGE}
                """
            }
        }

        stage('Push to Registry') {
            when {
                anyOf { branch 'main'; branch 'release/*' }
            }
            steps {
                withCredentials([usernamePassword(credentialsId: REGISTRY_CREDS, usernameVariable: 'REG_USER', passwordVariable: 'REG_PASS')]) {
                    sh """
                        echo "\$REG_PASS" | docker login ${REGISTRY} -u "\$REG_USER" --password-stdin
                        docker push ${IMAGE}
                        docker push ${REGISTRY}/${SERVICE_NAME}:latest
                    """
                }
            }
        }

        stage('Approval Gate (prod only)') {
            when {
                allOf {
                    expression { params.DEPLOY_ENV == 'prod' }
                    anyOf { branch 'main'; branch 'release/*' }
                }
            }
            steps {
                timeout(time: 24, unit: 'HOURS') {
                    input message: "Deploy ${SERVICE_NAME}:${IMAGE_TAG} to PRODUCTION?", submitter: 'clickkart-release-approvers'
                }
            }
        }

        stage('Deploy') {
            when {
                expression { params.DEPLOY_ENV != 'none' }
            }
            steps {
                sh """
                    kubectl config use-context clickkart-${params.DEPLOY_ENV}
                    kubectl -n clickkart-app set image deployment/${SERVICE_NAME} ${SERVICE_NAME}=${IMAGE} --record
                    kubectl -n clickkart-app rollout status deployment/${SERVICE_NAME} --timeout=180s
                """
            }
        }

        stage('Smoke Test') {
            when {
                expression { params.DEPLOY_ENV != 'none' }
            }
            steps {
                sh """
                    curl -fsS --retry 5 --retry-delay 5 \
                        https://${SERVICE_NAME}.${params.DEPLOY_ENV}.clickkart.internal/actuator/health \
                        | grep -q '"status":"UP"'
                """
            }
        }
    }

    post {
        failure {
            script {
                if (params.DEPLOY_ENV != 'none') {
                    echo "Build/deploy failed. Rollback is NOT automatic by design. To roll back:"
                    echo "  kubectl -n clickkart-app rollout undo deployment/${SERVICE_NAME}"
                    echo "  kubectl -n clickkart-app rollout status deployment/${SERVICE_NAME}"
                }
            }
        }
        always {
            sh 'docker logout ${REGISTRY} || true'
        }
    }
}
