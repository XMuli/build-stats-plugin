/*
 See https://github.com/jenkins-infra/pipeline-library/ for options.
 ci.jenkins.io runs this file after the plugin is hosted in the jenkinsci organization.
 */
buildPlugin(
  forkCount: '1C',
  useContainerAgent: true,
  configurations: [
    [platform: 'linux', jdk: 25],
    [platform: 'windows', jdk: 21],
  ]
)
