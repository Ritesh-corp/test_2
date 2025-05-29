pipeline {
    agent any

    environment {
        // Name of the Conda env
        CONDA_ENV = 'pr_an_py312'
        // Path to conda.sh—adjust if your install is elsewhere
        CONDA_INIT = "${HOME}/miniconda3/etc/profile.d/conda.sh"
    }

    parameters {
        string(name: 'PR_NUMBER', defaultValue: '', description: 'Pull Request Number from webhook')
        string(name: 'PR_URL',    defaultValue: '', description: 'Pull Request URL from webhook')
        string(name: 'GITHUB_TOKEN', defaultValue: '', description: 'GitHub Personal Access Token')
    }

    stages {
        stage('Clone Analysis Repo (repo2)') {
            steps {
                git(
                  url:           'https://github.com/Ritesh-corp/PR_analyser.git',
                  branch:        'main'
                )
            }
        }

        stage('Set Up Conda Environment') {
            steps {
                sh """
                  # Initialize conda
                  source "${CONDA_INIT}"
                  # Create env if it doesn't already exist
                  if ! conda info --envs | grep -q "^${CONDA_ENV}\\s"; then
                    conda create -y -n ${CONDA_ENV} python=3.12
                  fi
                  # Activate and install deps
                  conda activate ${CONDA_ENV}
                  pip install --upgrade pip
                  pip install -r requirements.txt
                  pip install -e .
                """
            }
        }

        stage('Run Impact Analysis') {
            steps {
                script {
                    if (!params.PR_NUMBER) {
                        error("Missing required parameter: PR_NUMBER")
                    }
                    def prUrl = params.PR_URL ?: "https://github.com/Ritesh-corp/test_ado/pull/${params.PR_NUMBER}"
                    echo "Analyzing PR: ${prUrl}"

                    sh """
                      source "${CONDA_INIT}"
                      conda activate ${CONDA_ENV}
                      python tests/analyze_pr.py "${prUrl}" "${params.GITHUB_TOKEN}" > analysis_output.json
                    """
                }
            }
        }

        stage('Archive Results') {
            steps {
                archiveArtifacts artifacts: 'analysis_output.json', fingerprint: true
            }
        }
    }

    post {
        success { echo "✅ Impact analysis completed." }
        failure { echo "❌ Impact analysis failed."  }
        always  { cleanWs() }
    }
}
