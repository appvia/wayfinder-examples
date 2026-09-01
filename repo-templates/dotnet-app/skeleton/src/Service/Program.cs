// ${{ .Inputs.description }}
//
// Serves three endpoints:
//
//   GET /         the service identity and release
//   GET /healthz  liveness  — is the process alive
//   GET /readyz   readiness — is the process ready to take traffic
//
// The liveness and readiness endpoints are what the Helm chart in charts/app
// wires up as probes, so keep them cheap and dependency-free. A readiness check
// that talks to a database takes the pod out of service the moment the database
// hiccups, which is rarely what you want.

var builder = WebApplication.CreateBuilder(args);

// Kestrel binds this rather than the default 5000, so the container listens
// where the chart expects. PORT is set by the chart from the template's port.
var port = Environment.GetEnvironmentVariable("PORT") ?? "${{ .Inputs.port }}";
builder.WebHost.UseUrls($"http://0.0.0.0:{port}");

builder.Services.AddHealthChecks();

var app = builder.Build();

// Set at build time by CI: the git tag for a release, the short SHA otherwise,
// so a running pod can always be traced back to a commit.
var release = Environment.GetEnvironmentVariable("RELEASE") ?? "dev";

// BUCKET_NAME is set by the Helm chart from the storage component's output. The
// workload reaches the bucket through its own cloud identity, so there are no
// credentials to read here.
var bucket = Environment.GetEnvironmentVariable("BUCKET_NAME") ?? string.Empty;

app.MapGet("/healthz", () => Results.Ok(new { status = "ok" }));
app.MapGet("/readyz", () => Results.Ok(new { status = "ok" }));

app.MapGet("/", () => Results.Ok(new
{
    service = "${{ .Inputs.serviceName }}",
    release,
    bucket,
}));

app.Run();

// Makes the entry point reachable from the test project's WebApplicationFactory.
// A top-level-statements program generates an internal Program class, which the
// test host cannot see without this.
public partial class Program;
