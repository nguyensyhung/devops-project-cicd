def call() {
    node {
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
        stage('Deploy') {
            echo "Deploying application..."
        }
    }
}

