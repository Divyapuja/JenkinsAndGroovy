pipeline {
    agent {
		label "master"
	}
	
	options {
		buildDiscarder(logRotator(numToKeepStr: '30'))
	}
    environment {
        AAP_ENV = "sandbox"
        AAP_AWS_REGION = "us-east-1"
    }

    stages {
        stage('Cycle EC2') {
            steps {
                withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: "	Junior-DevOps-aap-sandbox-datascience", secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
					script {
						wrap([$class: 'BuildUser']) {
							AAP_KEYPAIR = "${BUILD_USER_ID}"
							AAP_STACKNAME = "${AAP_KEYPAIR.replaceAll('\\.', '')}-EC2"
						}
                        //get stack id as per build user
						stackFilter = sh (
                            script: "aws cloudformation describe-stacks --region ${AAP_AWS_REGION} --stack-name ${AAP_STACKNAME} --query 'Stacks[*].StackId' --output text",
                            returnStdout: true
                        ).tokenize()
                        //get specific instances ids according to unique stack id 
						ec2List = sh (
                            script: "aws ec2 describe-instances --region ${AAP_AWS_REGION} --filter Name=tag:aws:cloudformation:stack-id,Values=${stackFilter} --output text --query 'Reservations[*].Instances[*].InstanceId'",
                            returnStdout: true
                        ).tokenize()
                        //iterate over all instances and fetch the instance current state
						for (instance in ec2List) {
                            instanceState = sh (
                                script: "aws ec2 describe-instances --region ${AAP_AWS_REGION} --instance-id ${instance} --query 'Reservations[*].Instances[*].State.Name'",
                                returnStdout: true
                            )
                            //stop the running instance and start the stopped instance
                            if(instanceState.contains("running")){
                                sh """
                                    aws ec2 stop-instances --region ${AAP_AWS_REGION} --instance-ids ${instance}
                                """
                            }else if(instanceState.contains("stopped")){
                                sh """
                                    aws ec2 start-instances --region ${AAP_AWS_REGION} --instance-ids ${instance}
                                """
                            }else{
                                println "Nothing to do!"
                            }
                        }
					}
                }
            }
        }
    }
}
