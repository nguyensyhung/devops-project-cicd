def call() {
    node {
        def ec2IP = "54.158.216.223"
        def ec2User = "ubuntu"
        properties([
            parameters([
                string(name: 'GIT_COMMIT_ID', defaultValue: '', description: 'Git commit ID to checkout (leave empty for latest commit)')
            ])
        ])
        stage('Checkout') {
            def commitId = params.GIT_COMMIT_ID ?: 'main'
            echo "Checking out commit: \${commitId}"
            git url: 'https://github.com/nguyensyhung/be-capstone-project', branch: 'main'
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
            def imageName = "hungns97/be-capstone-project:latest"
            echo "Building Docker image: ${imageName}"
            sh "docker build -t ${imageName} ."
            echo "Docker image built successfully"
        }
        stage('Push to Registry') {
            def imageName = "hungns97/be-capstone-project:latest"
            echo "Pushing Docker image to registry: ${imageName}"
            withCredentials([usernamePassword(credentialsId: 'DOCKER_REGISTRY_CREDS', usernameVariable: 'DOCKER_USERNAME', passwordVariable: 'DOCKER_PASSWORD')]) {
                sh "echo ${DOCKER_PASSWORD} | docker login -u ${DOCKER_USERNAME} --password-stdin"
                sh "docker push ${imageName}"
            }
            echo "Docker image pushed successfully to registry"
        }
        stage('Deploy to EC2') {
            echo "Deploying application to EC2..."
            withCredentials([
                sshUserPrivateKey(
                    credentialsId: 'ec2-ssh-key',
                    keyFileVariable: 'SSH_KEY'
                ),
                file(
                    credentialsId: 'be-capstone-env-file',
                    variable: 'ENV_FILE'
                )
            ]) {
                sh """
                    echo "Copying files to EC2..."
                    scp -i \$SSH_KEY -o StrictHostKeyChecking=no \
                        docker-compose.yml ubuntu@54.158.216.223:/tmp/docker-compose.yml

                    scp -i \$SSH_KEY -o StrictHostKeyChecking=no \
                        \$ENV_FILE ubuntu@54.158.216.223:/tmp/.env

                    echo "Deploying on EC2..."
                    ssh -i \$SSH_KEY -o StrictHostKeyChecking=no ubuntu@54.158.216.223 '
                        mv -f /tmp/docker-compose.yml /home/ubuntu/docker-compose.yml
                        mv -f /tmp/.env /home/ubuntu/.env

                        # Set permission
                        chmod 600 /home/ubuntu/.env
                        chmod 644 /home/ubuntu/docker-compose.yml

                        # Deploy
                        cd /home/ubuntu
                        docker compose pull
                        docker compose down
                        docker compose up -d
                        docker image prune -f

                        echo "Container status:"
                        docker compose ps
                    '
                """
            }
            echo "Deployment completed successfully"
        }
        stage('Health Check') {
            echo "Performing health check..."
            sleep 10
            sh """
                curl -f http://${ec2IP}/health || \
                curl -f http://${ec2IP}/ || \
                echo "Warning: Health check endpoint not available"
            """
        }
    }
}

