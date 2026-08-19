// Film Recommendation Engine — Content-based movie recommender using TMDb 5000.
//
// Usage:
//   dotnet run -- --movie "The Dark Knight Rises" --data-dir ../data
//   dotnet run -- --id 12 --data-dir ../data

using System.Text.Json;

var movie = "";
var id = -1;
var noDedup = false;
var dataDir = "dataset";

for (int i = 0; i < args.Length; i++)
{
    switch (args[i])
    {
        case "--movie" when i + 1 < args.Length: movie = args[++i]; break;
        case "--id" when i + 1 < args.Length: id = int.Parse(args[++i]); break;
        case "--no-dedup": noDedup = true; break;
        case "--data-dir" when i + 1 < args.Length: dataDir = args[++i]; break;
    }
}

if (string.IsNullOrEmpty(movie) && id < 0)
{
    Console.WriteLine("Error: provide --movie or --id");
    return;
}

Console.WriteLine("Loading dataset...");
var films = FilmLoader.Load(
    Path.Combine(dataDir, "tmdb_5000_movies.csv"),
    Path.Combine(dataDir, "tmdb_5000_credits.csv")
);

Console.WriteLine("Building film records...");

var factorsPath = Path.Combine(dataDir, "item_factors.csv");
var itemFactors = CollabFilter.LoadFactors(factorsPath);
if (itemFactors.Count > 0)
    Console.WriteLine($"Loaded collaborative factors for {itemFactors.Count} movies");
else
    Console.WriteLine("No collaborative factors found — using content-based only");

var targetIdx = id >= 0 ? id : FilmFinder.FindByTitle(films, movie);
if (targetIdx < 0)
{
    Console.WriteLine($"Error: could not find movie matching '{movie}'");
    return;
}

var target = films[targetIdx];
Console.WriteLine($"\nRecommendations for: {target.Title} ({target.Year})");
Console.WriteLine(new string('=', 60));

var results = Recommender.Recommend(films, targetIdx, !noDedup, itemFactors);
for (int i = 0; i < results.Count; i++)
{
    var r = results[i];
    var yearStr = r.Year > 0 ? r.Year.ToString() : "?";
    Console.WriteLine($"  {i + 1}. {r.Title} ({yearStr}) — IMDB: {r.Score:F1}");
}

const double CollabWeight = 0.4;

record Film(
    int TmdbId, string Title, string Genres, string PlotKeywords,
    string Director, string Actor1, string Actor2, string Actor3,
    int Year, double VoteAverage, int VoteCount, double Popularity);

record Candidate(string Title, int Year, double Score, int Votes, int Index)
{
    public double RankScore { get; set; }
}

static class FilmLoader
{
    public static List<Film> Load(string moviesPath, string creditsPath)
    {
        var movieRows = ReadCsv(moviesPath);
        var creditRows = ReadCsv(creditsPath);
        var films = new List<Film>();

        for (int i = 0; i < movieRows.Count; i++)
        {
            var m = movieRows[i];
            var c = i < creditRows.Count ? creditRows[i] : new Dictionary<string, string>();

            var year = 0;
            if (m.TryGetValue("release_date", out var rd) && rd.Length >= 4)
                int.TryParse(rd[..4], out year);

            double.TryParse(m.GetValueOrDefault("vote_average", "0"), out var va);
            int.TryParse(m.GetValueOrDefault("vote_count", "0"), out var vc);
            double.TryParse(m.GetValueOrDefault("popularity", "0"), out var pop);

            int.TryParse(m.GetValueOrDefault("id", "0"), out var tmdbId);

            films.Add(new Film(
                TmdbId: tmdbId,
                Title: m.GetValueOrDefault("title", ""),
                Genres: PipeNames(m.GetValueOrDefault("genres", "[]")),
                PlotKeywords: PipeNames(m.GetValueOrDefault("keywords", "[]")),
                Director: GetDirector(c.GetValueOrDefault("crew", "[]")),
                Actor1: GetCast(c.GetValueOrDefault("cast", "[]"), 0),
                Actor2: GetCast(c.GetValueOrDefault("cast", "[]"), 1),
                Actor3: GetCast(c.GetValueOrDefault("cast", "[]"), 2),
                Year: year,
                VoteAverage: va,
                VoteCount: vc,
                Popularity: pop
            ));
        }
        return films;
    }

    static List<Dictionary<string, string>> ReadCsv(string path)
    {
        var lines = new List<Dictionary<string, string>>();
        using var reader = new StreamReader(path);
        var headers = ParseCsvLine(reader.ReadLine()!);

        while (!reader.EndOfStream)
        {
            var values = ParseCsvLine(reader.ReadLine()!);
            var row = new Dictionary<string, string>();
            for (int i = 0; i < headers.Count && i < values.Count; i++)
                row[headers[i]] = values[i];
            lines.Add(row);
        }
        return lines;
    }

    static List<string> ParseCsvLine(string line)
    {
        var fields = new List<string>();
        var current = new System.Text.StringBuilder();
        bool inQuotes = false;

        for (int i = 0; i < line.Length; i++)
        {
            char ch = line[i];
            if (inQuotes)
            {
                if (ch == '"')
                {
                    if (i + 1 < line.Length && line[i + 1] == '"')
                    {
                        current.Append('"');
                        i++;
                    }
                    else
                        inQuotes = false;
                }
                else
                    current.Append(ch);
            }
            else
            {
                if (ch == '"')
                    inQuotes = true;
                else if (ch == ',')
                {
                    fields.Add(current.ToString());
                    current.Clear();
                }
                else
                    current.Append(ch);
            }
        }
        fields.Add(current.ToString());
        return fields;
    }

    static string PipeNames(string json)
    {
        try
        {
            var items = JsonSerializer.Deserialize<List<Dictionary<string, JsonElement>>>(json);
            if (items == null) return "";
            return string.Join("|", items
                .Where(i => i.ContainsKey("name"))
                .Select(i => i["name"].GetString() ?? ""));
        }
        catch { return ""; }
    }

    static string GetDirector(string json)
    {
        try
        {
            var crew = JsonSerializer.Deserialize<List<Dictionary<string, JsonElement>>>(json);
            if (crew == null) return "";
            var dir = crew.FirstOrDefault(m =>
                m.TryGetValue("job", out var j) && j.GetString() == "Director");
            return dir != null && dir.TryGetValue("name", out var n) ? n.GetString() ?? "" : "";
        }
        catch { return ""; }
    }

    static string GetCast(string json, int index)
    {
        try
        {
            var cast = JsonSerializer.Deserialize<List<Dictionary<string, JsonElement>>>(json);
            if (cast == null || index >= cast.Count) return "";
            return cast[index].TryGetValue("name", out var n) ? n.GetString() ?? "" : "";
        }
        catch { return ""; }
    }
}

static class FilmFinder
{
    public static int FindByTitle(List<Film> films, string query)
    {
        var q = query.Trim().ToLower();
        for (int i = 0; i < films.Count; i++)
            if (films[i].Title.Trim().ToLower() == q) return i;

        int bestIdx = -1, bestScore = 0;
        for (int i = 0; i < films.Count; i++)
        {
            int score = FuzzyRatio(q, films[i].Title.ToLower());
            if (score > bestScore) { bestScore = score; bestIdx = i; }
        }
        return bestScore >= 60 ? bestIdx : -1;
    }

    public static int FuzzyRatio(string a, string b)
    {
        int maxLen = Math.Max(a.Length, b.Length);
        if (maxLen == 0) return 100;
        int dist = LevenshteinDistance(a, b);
        return (int)((double)(maxLen - dist) / maxLen * 100);
    }

    static int LevenshteinDistance(string a, string b)
    {
        int la = a.Length, lb = b.Length;
        var d = new int[la + 1, lb + 1];
        for (int i = 0; i <= la; i++) d[i, 0] = i;
        for (int j = 0; j <= lb; j++) d[0, j] = j;
        for (int i = 1; i <= la; i++)
            for (int j = 1; j <= lb; j++)
            {
                int cost = a[i - 1] == b[j - 1] ? 0 : 1;
                d[i, j] = Math.Min(Math.Min(d[i - 1, j] + 1, d[i, j - 1] + 1), d[i - 1, j - 1] + cost);
            }
        return d[la, lb];
    }

    public static bool IsSequel(string t1, string t2) => FuzzyRatio(t1.ToLower(), t2.ToLower()) > 50;
}

static class CollabFilter
{
    public static Dictionary<int, double[]> LoadFactors(string path)
    {
        var factors = new Dictionary<int, double[]>();
        if (!File.Exists(path)) return factors;

        using var reader = new StreamReader(path);
        reader.ReadLine(); // skip header
        while (!reader.EndOfStream)
        {
            var line = reader.ReadLine();
            if (string.IsNullOrEmpty(line)) continue;
            var fields = line.Split(',');
            if (fields.Length < 2) continue;
            if (!int.TryParse(fields[0], out var tmdbId)) continue;
            var vec = new double[fields.Length - 1];
            for (int i = 1; i < fields.Length; i++)
                double.TryParse(fields[i], out vec[i - 1]);
            factors[tmdbId] = vec;
        }
        return factors;
    }

    public static double Similarity(Dictionary<int, double[]> factors, int idA, int idB)
    {
        if (!factors.TryGetValue(idA, out var va) || !factors.TryGetValue(idB, out var vb))
            return 0.0;
        double dot = 0;
        for (int i = 0; i < va.Length && i < vb.Length; i++)
            dot += va[i] * vb[i];
        return Math.Max(0.0, dot);
    }
}

static class Recommender
{
    public static List<Candidate> Recommend(List<Film> films, int targetIdx, bool dedupSequels,
                                            Dictionary<int, double[]>? itemFactors = null)
    {
        var neighborIdxs = FindNeighbors(films, targetIdx, 31);

        int maxVotes = 0;
        var candidates = new List<Candidate>();
        foreach (var idx in neighborIdxs)
        {
            var f = films[idx];
            if (f.VoteCount > maxVotes) maxVotes = f.VoteCount;
            candidates.Add(new Candidate(f.Title, f.Year, f.VoteAverage, f.VoteCount, idx));
        }

        var mainTitle = candidates[0].Title;
        double mainYear = candidates[0].Year;
        int targetTmdb = films[targetIdx].TmdbId;
        var factors = itemFactors ?? new Dictionary<int, double[]>();

        foreach (var c in candidates)
        {
            if (FilmFinder.IsSequel(mainTitle, c.Title))
            {
                c.RankScore = 0;
                continue;
            }
            double fact1 = mainYear > 0 && c.Year > 0 ? Gaussian(mainYear, c.Year, 20) : 1.0;
            double fact2 = maxVotes > 0 ? Gaussian(c.Votes, maxVotes, maxVotes) : 0.0;
            double contentScore = c.Score * c.Score * fact1 * fact2;
            double csim = CollabFilter.Similarity(factors, targetTmdb, films[c.Index].TmdbId);
            if (csim > 0)
                c.RankScore = (1 - CollabWeight) * contentScore + CollabWeight * csim * c.Score * c.Score;
            else
                c.RankScore = contentScore;
        }

        candidates.Sort((a, b) => b.RankScore.CompareTo(a.RankScore));

        var selected = new List<Candidate>();
        foreach (var c in candidates)
        {
            if (selected.Count >= 5) break;
            if (selected.Any(s => s.Title == c.Title || FilmFinder.IsSequel(c.Title, s.Title)))
                continue;
            selected.Add(c);
        }

        if (dedupSequels)
        {
            var remove = new HashSet<string>();
            for (int i = 0; i < selected.Count; i++)
                for (int j = i + 1; j < selected.Count; j++)
                    if (FilmFinder.IsSequel(selected[i].Title, selected[j].Title))
                        remove.Add(selected[i].Year < selected[j].Year ? selected[j].Title : selected[i].Title);

            selected = selected.Where(s => !remove.Contains(s.Title)).ToList();

            foreach (var c in candidates)
            {
                if (selected.Count >= 5) break;
                if (selected.Any(s => s.Title == c.Title || FilmFinder.IsSequel(c.Title, s.Title)))
                    continue;
                selected.Add(c);
            }
        }

        return selected.Take(5).ToList();
    }

    static List<string> GetFeatures(Film film)
    {
        var features = new List<string>();
        if (!string.IsNullOrEmpty(film.Director)) features.Add(film.Director);
        foreach (var a in new[] { film.Actor1, film.Actor2, film.Actor3 })
            if (!string.IsNullOrEmpty(a)) features.Add(a);
        if (!string.IsNullOrEmpty(film.PlotKeywords))
            features.AddRange(film.PlotKeywords.Split('|').Where(s => s != ""));
        if (!string.IsNullOrEmpty(film.Genres))
            features.AddRange(film.Genres.Split('|').Where(s => s != ""));
        return features;
    }

    static List<int> FindNeighbors(List<Film> films, int targetIdx, int n)
    {
        var targetFeatures = GetFeatures(films[targetIdx]);
        var allGenres = new HashSet<string>();
        foreach (var f in films)
            if (!string.IsNullOrEmpty(f.Genres))
                foreach (var g in f.Genres.Split('|'))
                    allGenres.Add(g);

        var featureSet = new HashSet<string>(targetFeatures);
        featureSet.UnionWith(allGenres);
        var featureList = featureSet.ToList();

        var vectors = new List<int[]>();
        foreach (var film in films)
        {
            var filmFeats = new HashSet<string>(GetFeatures(film));
            var vec = featureList.Select(f => filmFeats.Contains(f) ? 1 : 0).ToArray();
            vectors.Add(vec);
        }

        var targetVec = vectors[targetIdx];
        var dists = films.Select((_, i) => (Index: i, Dist: Euclidean(targetVec, vectors[i])))
            .OrderBy(x => x.Dist)
            .Take(n)
            .Select(x => x.Index)
            .ToList();
        return dists;
    }

    static double Euclidean(int[] a, int[] b)
    {
        double sum = 0;
        for (int i = 0; i < a.Length; i++)
        {
            int d = a[i] - b[i];
            sum += d * d;
        }
        return Math.Sqrt(sum);
    }

    static double Gaussian(double x, double y, double sigma)
    {
        if (sigma == 0) return 0;
        return Math.Exp(-Math.Pow(x - y, 2) / (2 * sigma * sigma));
    }
}
