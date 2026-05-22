module.exports = {
  platform: 'github',
  repositories: [process.env.GITHUB_REPOSITORY],
  hostRules: [
    {
      matchHost: 'repository.caplin.com',
      hostType: 'maven',
      username: process.env.CAPLIN_USERNAME,
      password: process.env.CAPLIN_PASSWORD,
    },
  ],
};
