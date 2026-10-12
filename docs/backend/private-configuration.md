# Private application configuration

Actual connection credentials, mail passwords and provider keys belong outside Git and outside the WAR. `src/main/resources/application.properties.example` contains only public placeholders; `application.properties` is ignored, excluded from Maven resources and excluded from the packaged WAR, including stale build copies. `.dockerignore` excludes that file and `.local` from the build context, so a local Docker build cannot copy them into its builder layers/cache either.

Keep local settings in `.local/application.properties` and select the file with the environment variable `TC_CONFIG_FILE`, or JVM property `-Dtc.config.file=<absolute-path>`. System properties and environment overrides still take precedence over values in the private file. An explicitly selected missing/unreadable file fails closed without printing its path/content or falling back to a different database.

On this workstation the ignored `.local/use-project-config.ps1` selects the existing private file:

```powershell
. ./.local/use-project-config.ps1
# Start the application using the project's normal runtime command in this environment.
```

Do not copy `.local`, `application.properties`, private fixture files or the generated runtime credentials to Git, screenshots, issue comments or build artifacts. Deployment uses its secret store/environment or an external mounted private file. Docker publishing already uses GitHub Secrets; test workflows use isolated service databases, fictitious email addresses and generated or run-specific test passwords. Their SQL `sa` identity is confined to disposable test containers. The default web context smoke step tests the web server root URL; XML `getroot()` means the root element of a test report.

Removing secrets from the current tree does not erase earlier Git commits. Credentials and provider keys that were previously committed must be replaced by their owners; this change does not rewrite shared history or reset existing accounts.

The required CI configuration check includes every tracked `.yml`/`.yaml` file. It rejects tracked private configuration, private key material, literal Azure SQL endpoints and email addresses outside reserved example/test domains. It reports only affected filenames, never matching lines or secret contents. CI fixture logins and generated passwords remain unchanged; registry authentication still uses the runtime GitHub identity with `secrets.GITHUB_TOKEN`. The default web context smoke check runs the same command with a clearer step name.
