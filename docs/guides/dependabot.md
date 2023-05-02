# Dependabot

[Dependabot][dependabot] is part of GitHub's [Code Security][gh-code-security] toolset which focuses on automatic 
maintenance of project dependencies. As such work is important but also tedious, Dependabot has been enabled for 
this project to avoid the tedium while reaping the benefits.

## How it Works

Dependabot is configured in the [dependabot.yml](../../.github/dependabot.yml) file to automatically scan and update 
Maven dependencies. For each outdated dependency Dependabot creates a Pull Request which is then treated as any Pull 
Request would be, including automatic verification and review requests.

Sometimes Dependabot's Pull Requests may fail, or other interaction is required. For these cases one can use 
[Dependabot chat ops][dependabot-chatops] -like commands, for example rebasing is done simply by adding a comment to 
the Pull Request with content `@dependabot rebase`.

[dependabot]: https://docs.github.com/en/code-security/dependabot
[gh-code-security]: https://docs.github.com/en/code-security
[dependabot-chatops]: https://docs.github.com/en/code-security/dependabot/working-with-dependabot/managing-pull-requests-for-dependency-updates#managing-dependabot-pull-requests-with-comment-commands
