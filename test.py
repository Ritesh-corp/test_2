
# Test file
import pytest
import asyncio
from pr_agent.agent.pr_agent import PRAgent
from pr_agent.algo.ai_handlers.firefall_ai_handler import FirefallAIHandler
from pr_agent.config_loader import get_settings
import os
import dotenv
dotenv.load_dotenv()
github_token = os.getenv("GITHUB_PERSONAL_TOKEN")
async def get_pr_analysis(pr_url):
    # Configure settings
    get_settings().set("CONFIG.git_provider", "github")
    get_settings().set("github.user_token", github_token)
    get_settings().set("CONFIG.model", "firefall")
    get_settings().set("CONFIG.max_model_tokens", 128000)
    get_settings().set("CONFIG.publish_output", False)
    get_settings().set("CONFIG.use_repo_settings_file", False)
    get_settings().set("CONFIG.enable_ai_metadata", False)
    get_settings().set("CONFIG.is_auto_command", False)
    
    # Initialize the PR agent with Firefall handler
    agent = PRAgent(ai_handler=FirefallAIHandler)
    
    # Execute the request and get the response
    success, response = await agent.handle_request(pr_url, "/analyse_impact")
    
    if not success:
        raise Exception("Failed to get PR analysis")
        
    return response

@pytest.mark.asyncio
async def test_firefall_model_integration():
    test_pr_url = "https://github.com/Ritesh-corp/test_ado/pull/1"
    response = await get_pr_analysis(test_pr_url)
    print("
Model Response:")
    print(response)
    return response

if __name__ == "__main__":
    response = asyncio.run(test_firefall_model_integration())
