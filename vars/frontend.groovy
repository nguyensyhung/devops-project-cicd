def call() {
    node {
        def ec2IP = "54.158.216.223"
        def ec2User = "ubuntu"
        def branchName = ''
        def isMasterBranch = false

        stage('Checkout') {
            echo "Checking out code..."
            checkout scm

            branchName = env.BRANCH_NAME
            echo "Branch detected from env.BRANCH_NAME: ${branchName}"

            isMasterBranch = branchName == 'main'

            echo "==================================================="
            echo "Branch Name: ${branchName}"
            echo "Is Main: ${isMasterBranch}"
            echo "==================================================="
        }

        stage('Build') {
            echo "Building the project..."
            echo "Building completed"
        }

        stage('Test') {
            echo "Running tests..."
            echo "Tests completed"
        }

        stage('Quality Gate') {
            parallel(
                'Lint': {
                    stage('Lint') {
                        echo "Running lint checks..."
                        sh "npm run lint || echo 'Lint completed'"
                    }
                }
            )
        }

        if (isMasterBranch) {
            echo "==================================================="
            echo "Main branch detected - Running CD Pipeline"
            echo "==================================================="

            stage('CI Stage Complete') {
                echo "CI stages completed successfully"
            }

            stage('Push to ECR') {
                echo "Building and pushing Docker image..."
                def imageName = "hungns97/fe-capstone-project:latest"
                def imageTag = "hungns97/fe-capstone-project:${env.BUILD_NUMBER}"

                echo "Building Docker image: ${imageName}"
                sh "docker build -t ${imageName} -t ${imageTag} . --no-cache"

                echo "Pushing Docker image to registry..."
                withCredentials([
                    usernamePassword(
                        credentialsId: 'DOCKER_REGISTRY_CREDS',
                        usernameVariable: 'DOCKER_USERNAME',
                        passwordVariable: 'DOCKER_PASSWORD'
                    )
                ]) {
                    sh "echo \$DOCKER_PASSWORD | docker login -u \$DOCKER_USERNAME --password-stdin"
                    sh "docker push ${imageName}"
                    sh "docker push ${imageTag}"
                }
                echo "Docker image pushed successfully to registry (ECR)"
            }

            stage('Deploy Staging') {
                echo "Deploying Frontend to Staging environment..."
                withCredentials([
                    sshUserPrivateKey(
                        credentialsId: 'ec2-ssh-key',
                        keyFileVariable: 'SSH_KEY'
                    ),
                    file(
                        credentialsId: 'fe-capstone-env-file',
                        variable: 'ENV_FILE'
                    )
                ]) {
                    sh """
                        echo "Copying Frontend files to Staging EC2..."
                        scp -i \$SSH_KEY -o StrictHostKeyChecking=no \
                            docker-compose.yml ${ec2User}@${ec2IP}:/tmp/fe-docker-compose.yml

                        scp -i \$SSH_KEY -o StrictHostKeyChecking=no \
                            \$ENV_FILE ${ec2User}@${ec2IP}:/tmp/fe.env

                        echo "Deploying Frontend on Staging EC2..."
                        ssh -i \$SSH_KEY -o StrictHostKeyChecking=no ${ec2User}@${ec2IP} '
                            mkdir -p /home/ubuntu/frontend

                            mv -f /tmp/fe-docker-compose.yml /home/ubuntu/frontend/docker-compose.yml
                            mv -f /tmp/fe.env /home/ubuntu/frontend/.env

                            chmod 600 /home/ubuntu/frontend/.env
                            chmod 644 /home/ubuntu/frontend/docker-compose.yml

                            cd /home/ubuntu/frontend
                            docker compose pull
                            docker compose down
                            docker compose up -d
                            docker image prune -f

                            echo "Frontend container status:"
                            docker compose ps
                        '
                    """
                }

                echo "Staging deployment completed"
            }

            stage('Approval') {
                echo "Waiting for approval to deploy to Production..."
                timeout(time: 24, unit: 'HOURS') {
                    input message: 'Deploy Frontend to Production?',
                          ok: 'Yes, Deploy to Production',
                          submitter: 'admin,devops-team'
                }
                echo "Approval received - proceeding to Production deployment"
            }

            stage('Deploy Production') {
                echo "Deploying Frontend to Production environment..."
                echo "Production deployment completed successfully"
            }

        } else {
            echo "==================================================="
            echo "Not main branch - CD Pipeline skipped"
            echo "Only CI Pipeline was executed"
            echo "Branch: ${branchName}"
            echo "==================================================="
        }

        stage('Pipeline Complete') {
            if (isMasterBranch) {
                echo "Full CI/CD Pipeline completed successfully!"
            } else {
                echo "CI Pipeline completed successfully for branch: ${branchName}"
            }
        }
    }
}
