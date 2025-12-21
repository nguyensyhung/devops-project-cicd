def call() {
    def ec2IP = "54.158.216.223"
    def ec2User = "ubuntu"
    node {
        properties([
            parameters([
                string(name: 'GIT_COMMIT_ID', defaultValue: '', description: 'Git commit ID to checkout (leave empty for latest commit)')
            ])
        ])
        stage('Checkout') {
            def commitId = params.GIT_COMMIT_ID ?: 'main'
            echo "Checking out commit: \${commitId}"
            git url: 'https://github.com/nguyensyhung/fe-capstone-project', branch: 'main'
            if (params.GIT_COMMIT_ID && params.GIT_COMMIT_ID.trim() != '') {
                sh "git checkout \${params.GIT_COMMIT_ID}"
            }
        }
        stage('Run Lint Fix') {
            parallel(
                'Lint': {
                    stage('Lint') {
                        echo "Running lint checks..."
                        sh "npm run lint || echo 'Lint completed'"
                    }
                }
            )
        }
        stage('Build Docker Image') {
            echo "Building the project..."
            def imageName = "hungns97/fe-capstone-project:latest"
            echo "Building Docker image: ${imageName}"
            sh "docker build -t ${imageName} ."
            echo "Docker image built successfully"
        }
        stage('Push to Registry') {
            def imageName = "hungns97/fe-capstone-project:latest"
            echo "Pushing Docker image to registry: ${imageName}"
            withCredentials([usernamePassword(credentialsId: 'DOCKER_REGISTRY_CREDS', usernameVariable: 'DOCKER_USERNAME', passwordVariable: 'DOCKER_PASSWORD')]) {
                sh "echo ${DOCKER_PASSWORD} | docker login -u ${DOCKER_USERNAME} --password-stdin"
                sh "docker push ${imageName}"
            }
            echo "Docker image pushed successfully to registry"
        }
        stage('Deploy to EC2') {
            echo "Deploying Frontend to EC2..."
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
                    echo "Copying Frontend files to EC2..."
                    scp -i \$SSH_KEY -o StrictHostKeyChecking=no \
                        docker-compose.yml ${ec2User}@${ec2IP}:/tmp/fe-docker-compose.yml

                    scp -i \$SSH_KEY -o StrictHostKeyChecking=no \
                        \$ENV_FILE ${ec2User}@${ec2IP}:/tmp/fe.env

                    echo "Deploying Frontend on EC2..."
                    ssh -i \$SSH_KEY -o StrictHostKeyChecking=no ${ec2User}@${ec2IP} '
                        mkdir -p /home/ubuntu/frontend

                        # Move files
                        mv -f /tmp/fe-docker-compose.yml /home/ubuntu/frontend/docker-compose.yml
                        mv -f /tmp/fe.env /home/ubuntu/frontend/.env

                        # Set permissions
                        chmod 600 /home/ubuntu/frontend/.env
                        chmod 644 /home/ubuntu/frontend/docker-compose.yml

                        # Deploy
                        cd /home/ubuntu/frontend
                        docker compose pull
                        docker compose down
                        docker compose up -d --build
                        docker image prune -f

                        echo "Frontend container status:"
                        docker compose ps
                    '
                """
            }
            echo "Frontend deployment completed successfully"
        }
    }
}

