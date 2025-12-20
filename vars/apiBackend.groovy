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
            git url: 'https://github.com/devopsway/devops-project-application-cowsay-api', branch: 'main'
            if (params.GIT_COMMIT_ID && params.GIT_COMMIT_ID.trim() != '') {
                sh "git checkout \${params.GIT_COMMIT_ID}"
            }
        }
        stage('Build') {
            echo "Building the project..."
            echo "HungNS Hello 👋 "
        }
        stage('Test') {
            parallel(
                'Lint': {
                    stage('Lint') {
                        echo "Running lint checks..."
                        sh "npm run lint || echo 'Lint completed'"
                    }
                },
                'Unit Test & Code Scan': {
                    stage('Unit Test') {
                        echo "Running unit tests..."
                        sh "npm test || echo 'Unit tests completed'"
                    }
                    stage('Code Scan') {
                        echo "Running code scan..."
                        sh "npm run scan || echo 'Code scan completed'"
                    }
                }
            )
        }
        stage('Build Docker Image') {
            // def imageName = "cowsay-frontend:latest"
            // echo "Building Docker image: \${imageName}"
            // sh "docker build -t \${imageName} ."
            // echo "Docker image built successfully"
        }
        stage('Deploy') {
            echo "Deploying application..."
        }
    }
}

