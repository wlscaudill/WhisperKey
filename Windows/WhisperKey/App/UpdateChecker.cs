using System.Reflection;
using System.Text.Json;

namespace WhisperKeys.App;

public class UpdateChecker
{
    private const string ReleasesUrl = "https://api.github.com/repos/wlscaudill/WhisperKey/releases/latest";
    private const string TagPrefix = "windows-v";

    public record ReleaseInfo(string TagName, string Version, string ReleaseNotes, string ZipUrl, string HtmlUrl);

    public async Task<ReleaseInfo?> CheckForUpdateAsync(CancellationToken cancellationToken = default)
    {
        using var http = new HttpClient();
        http.Timeout = TimeSpan.FromSeconds(15);
        http.DefaultRequestHeaders.Add("User-Agent", "WhisperKey");
        http.DefaultRequestHeaders.Add("Accept", "application/vnd.github+json");

        HttpResponseMessage response;
        try
        {
            response = await http.GetAsync(ReleasesUrl, cancellationToken);
        }
        catch
        {
            return null;
        }

        if (!response.IsSuccessStatusCode)
            return null;

        var json = await response.Content.ReadAsStringAsync(cancellationToken);
        using var doc = JsonDocument.Parse(json);
        var root = doc.RootElement;

        var tagName = root.GetProperty("tag_name").GetString() ?? "";
        if (!tagName.StartsWith(TagPrefix))
            return null;

        var remoteVersion = tagName[TagPrefix.Length..];
        var localVersion = GetInstalledVersion();

        if (!IsNewerVersion(remoteVersion, localVersion))
            return null;

        // Find zip asset
        string? zipUrl = null;
        foreach (var asset in root.GetProperty("assets").EnumerateArray())
        {
            var name = asset.GetProperty("name").GetString() ?? "";
            if (name.EndsWith(".zip", StringComparison.OrdinalIgnoreCase))
            {
                zipUrl = asset.GetProperty("browser_download_url").GetString();
                break;
            }
        }

        if (zipUrl == null)
            return null;

        var releaseNotes = root.TryGetProperty("body", out var bodyProp)
            ? bodyProp.GetString()?.Trim() ?? ""
            : "";

        var htmlUrl = root.TryGetProperty("html_url", out var htmlProp)
            ? htmlProp.GetString() ?? ""
            : "";

        return new ReleaseInfo(tagName, remoteVersion, releaseNotes, zipUrl, htmlUrl);
    }

    private static string GetInstalledVersion()
    {
        var version = Assembly.GetExecutingAssembly().GetName().Version;
        if (version == null)
            return "0.0";
        return $"{version.Major}.{version.Minor}";
    }

    internal static bool IsNewerVersion(string remote, string local)
    {
        var remoteParts = remote.Split('.').Select(s => int.TryParse(s, out var n) ? n : 0).ToArray();
        var localParts = local.Split('.').Select(s => int.TryParse(s, out var n) ? n : 0).ToArray();
        var maxLen = Math.Max(remoteParts.Length, localParts.Length);

        for (int i = 0; i < maxLen; i++)
        {
            var r = i < remoteParts.Length ? remoteParts[i] : 0;
            var l = i < localParts.Length ? localParts[i] : 0;
            if (r > l) return true;
            if (r < l) return false;
        }
        return false;
    }
}
