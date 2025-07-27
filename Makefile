github-mcp-server:
	@set -a; \
	. $(CURDIR)/.env; \
	set +a; \
	docker run -i --rm \
	-e GITHUB_PERSONAL_ACCESS_TOKEN=$$GITHUB_PERSONAL_ACCESS_TOKEN \
    -e GITHUB_DYNAMIC_TOOLSETS=1 \
	ghcr.io/github/github-mcp-server