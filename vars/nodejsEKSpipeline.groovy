def call(configMap) {
    pipeline {
        agent {
            label 'AGENT-1'
        }

        environment {
            REGION = "us-east-1"
            ACC_ID = "139355608147"
            COMPONENT = configMap.get('COMPONENT')
            PROJECT = configMap.get('PROJECT')
            appVersion = ''

        }
       
       parameters {
        booleanParam(name: 'deploy', defaultValue: false, description: 'Toggle this value')
       }

       stages {
            stage("app version"){
                steps {
                    script {
                      // Read the file object directly
                    def packageJson = readJSON file: 'package.json'
                    appVersion = packageJson.version
                    def name = packageJson.name
                    echo "Building ${name} version ${appVersion}"
                    }

                }
            }

            stage('Debug') {
    steps {
        sh '''
            echo "===== USER ====="
            whoami
            id

            echo "===== SUDO ====="
            sudo -n whoami

            echo "===== NODE ====="
            node -v || true
            npm -v || true

            echo "===== SUDO PATH ====="
            which sudo
            sudo -V | head -1
        '''
    }
}

stage("Install Dependencies") {
    steps {
        sh '''
            sudo -n dnf module disable nodejs -y
            sudo -n dnf module enable nodejs:20 -y
            sudo -n dnf install nodejs -y
            npm install
        '''
    }
}

         /*    stage("Install Dependencies"){
                steps{
                    script{
                        sh """
                           sudo dnf module disable nodejs -y
                           sudo dnf module enable nodejs:20 -y
                           sudo dnf install nodejs -y 
                           npm install
                        """
                    }
                }
            } */

            stage("unit testing"){
                steps{
                    echo "unit tests"
                }
            }

            stage('sonar scan'){
                
                 environment {
                    scannerHome = tool 'sonar-7.2' //sonarqube server environment
                 }
                 steps {
                    dir('catalogue') {
                    script {
                    withSonarQubeEnv('sonar-7.2') {
                        sh "${scannerHome}/bin/sonar-scanner"
                     }
                   }
                 }
              }
            }

            stage('Quality Gate') {
            steps {
                timeout(time: 5, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
          }

            stage('Docker Build Image') {
                steps {
                    script {
                       withCredentials(credentials: 'aws-creds', region: 'us-east-1') {
                            sh """
                                aws ecr get-login-password --region ${REGION} | docker login --username AWS --password-stdin ${ACC_ID}.dkr.ecr.us-east-1.amazonaws.com
                                docker build -t ${ACC_ID}.dkr.ecr.region.amazonaws.com/${PROJECT}/${COMPONENT}:${appVersion} .
                                docker push aws_account_id.dkr.ecr.region.amazonaws.com/${PROJECT}/${COMPONENT}:${appVersion}
                            """
                       }
                    }
                }
            }

            stage('Trigger Deploye') {
                when {
                    expression {params.deploy}
                }
            steps {
                script {
                    build job: 'catalogue-cd',
                    parameters: [
                        string(name: 'appVersion', value: '${appVersion}'),
                        string(name: 'deploy_to', value: 'dev')
                    ]
                }
            }
       }
    }
}
}