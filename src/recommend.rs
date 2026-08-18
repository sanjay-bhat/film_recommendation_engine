// Film Recommendation Engine — Content-based movie recommender using TMDb 5000.
//
// Usage:
//   cargo run -- --movie "The Dark Knight Rises" --data-dir ../data
//   cargo run -- --id 12 --data-dir ../data
//
// Build:
//   rustc recommend.rs -o recommend

use std::collections::{HashMap, HashSet};
use std::env;
use std::fs;
use std::process;

fn main() {
    let args: Vec<String> = env::args().collect();
    let mut movie = String::new();
    let mut id: i32 = -1;
    let mut no_dedup = false;
    let mut data_dir = "dataset".to_string();

    let mut i = 1;
    while i < args.len() {
        match args[i].as_str() {
            "--movie" if i + 1 < args.len() => { i += 1; movie = args[i].clone(); }
            "--id" if i + 1 < args.len() => { i += 1; id = args[i].parse().unwrap_or(-1); }
            "--no-dedup" => no_dedup = true,
            "--data-dir" if i + 1 < args.len() => { i += 1; data_dir = args[i].clone(); }
            _ => {}
        }
        i += 1;
    }

    if movie.is_empty() && id < 0 {
        eprintln!("Error: provide --movie or --id");
        process::exit(1);
    }

    println!("Loading dataset...");
    let movies_path = format!("{}/tmdb_5000_movies.csv", data_dir);
    let credits_path = format!("{}/tmdb_5000_credits.csv", data_dir);

    let movie_rows = match read_csv(&movies_path) {
        Ok(r) => r,
        Err(e) => { eprintln!("Error loading movies: {}", e); process::exit(1); }
    };
    let credit_rows = match read_csv(&credits_path) {
        Ok(r) => r,
        Err(e) => { eprintln!("Error loading credits: {}", e); process::exit(1); }
    };

    println!("Building film records...");
    let films = build_films(&movie_rows, &credit_rows);

    let target_idx = if !movie.is_empty() {
        let idx = find_by_title(&films, &movie);
        if idx < 0 {
            eprintln!("Error: could not find movie matching '{}'", movie);
            process::exit(1);
        }
        idx as usize
    } else {
        id as usize
    };

    let target = &films[target_idx];
    println!("\nRecommendations for: {} ({})", target.title, target.year);
    println!("{}", "=".repeat(60));

    let results = recommend(&films, target_idx, !no_dedup);
    for (i, r) in results.iter().enumerate() {
        let year_str = if r.year > 0 { r.year.to_string() } else { "?".to_string() };
        println!("  {}. {} ({}) — IMDB: {:.1}", i + 1, r.title, year_str, r.score);
    }
}

struct Film {
    title: String,
    genres: String,
    plot_keywords: String,
    director: String,
    actor1: String,
    actor2: String,
    actor3: String,
    year: i32,
    vote_average: f64,
    vote_count: i32,
}

#[derive(Clone)]
struct Candidate {
    title: String,
    year: i32,
    score: f64,
    votes: i32,
    #[allow(dead_code)]
    index: usize,
    rank_score: f64,
}

fn read_csv(path: &str) -> Result<Vec<HashMap<String, String>>, String> {
    let content = fs::read_to_string(path).map_err(|e| e.to_string())?;
    let mut lines = content.lines();
    let header_line = lines.next().ok_or("Empty CSV")?;
    let headers = parse_csv_line(header_line);

    let mut rows = Vec::new();
    for line in lines {
        if line.trim().is_empty() { continue; }
        let values = parse_csv_line(line);
        let mut row = HashMap::new();
        for (i, h) in headers.iter().enumerate() {
            if i < values.len() {
                row.insert(h.clone(), values[i].clone());
            }
        }
        rows.push(row);
    }
    Ok(rows)
}

fn parse_csv_line(line: &str) -> Vec<String> {
    let mut fields = Vec::new();
    let mut current = String::new();
    let mut in_quotes = false;
    let chars: Vec<char> = line.chars().collect();
    let mut i = 0;

    while i < chars.len() {
        let ch = chars[i];
        if in_quotes {
            if ch == '"' {
                if i + 1 < chars.len() && chars[i + 1] == '"' {
                    current.push('"');
                    i += 1;
                } else {
                    in_quotes = false;
                }
            } else {
                current.push(ch);
            }
        } else if ch == '"' {
            in_quotes = true;
        } else if ch == ',' {
            fields.push(current.clone());
            current.clear();
        } else {
            current.push(ch);
        }
        i += 1;
    }
    fields.push(current);
    fields
}

fn extract_names_from_json(json_str: &str) -> Vec<String> {
    let mut names = Vec::new();
    let trimmed = json_str.trim();
    if !trimmed.starts_with('[') { return names; }

    let mut depth = 0;
    let mut in_str = false;
    let mut escape = false;
    let mut current_key = String::new();
    let mut current_val = String::new();
    let mut reading_key = false;
    let mut reading_val = false;
    let mut found_name_key = false;

    for ch in trimmed.chars() {
        if escape { escape = false; continue; }
        if ch == '\\' && in_str { escape = true; continue; }

        if ch == '"' && !escape {
            if !in_str {
                in_str = true;
                if reading_key || reading_val { /* continue */ }
            } else {
                in_str = false;
                if reading_key {
                    reading_key = false;
                    found_name_key = current_key == "name";
                    current_key.clear();
                } else if reading_val && found_name_key {
                    names.push(current_val.clone());
                    current_val.clear();
                    reading_val = false;
                    found_name_key = false;
                } else {
                    current_val.clear();
                    reading_val = false;
                }
            }
            continue;
        }

        if in_str {
            if reading_key { current_key.push(ch); }
            else if reading_val { current_val.push(ch); }
            continue;
        }

        match ch {
            '{' => { depth += 1; }
            '}' => { depth -= 1; found_name_key = false; }
            ':' if depth > 0 => { reading_val = true; }
            ',' if depth > 0 => { found_name_key = false; }
            _ => {}
        }

        if ch == '"' || (depth > 0 && !in_str && (ch == '{' || ch == ',')) {
            if !reading_val { reading_key = true; }
        }
    }
    names
}

fn pipe_names(json_str: &str) -> String {
    extract_names_from_json(json_str).join("|")
}

fn get_director(json_str: &str) -> String {
    let trimmed = json_str.trim();
    if !trimmed.starts_with('[') { return String::new(); }

    let mut in_str = false;
    let mut escape = false;
    let mut depth = 0;
    let mut objects: Vec<String> = Vec::new();
    let mut current_obj = String::new();

    for ch in trimmed.chars() {
        if escape { escape = false; current_obj.push(ch); continue; }
        if ch == '\\' { escape = true; current_obj.push(ch); continue; }
        if ch == '"' { in_str = !in_str; current_obj.push(ch); continue; }
        if in_str { current_obj.push(ch); continue; }

        match ch {
            '{' => { depth += 1; current_obj.push(ch); }
            '}' => {
                depth -= 1;
                current_obj.push(ch);
                if depth == 0 {
                    objects.push(current_obj.clone());
                    current_obj.clear();
                }
            }
            _ => { if depth > 0 { current_obj.push(ch); } }
        }
    }

    for obj in &objects {
        if obj.contains("\"job\"") && obj.contains("\"Director\"") {
            let names = extract_names_from_json(&format!("[{}]", obj));
            if let Some(name) = names.first() {
                return name.clone();
            }
        }
    }
    String::new()
}

fn get_cast_member(json_str: &str, index: usize) -> String {
    let names = extract_names_from_json(json_str);
    names.get(index).cloned().unwrap_or_default()
}

fn build_films(movies: &[HashMap<String, String>], credits: &[HashMap<String, String>]) -> Vec<Film> {
    let mut films = Vec::with_capacity(movies.len());
    for (i, m) in movies.iter().enumerate() {
        let year = m.get("release_date")
            .and_then(|rd| if rd.len() >= 4 { rd[..4].parse().ok() } else { None })
            .unwrap_or(0);
        let va = m.get("vote_average").and_then(|v| v.parse().ok()).unwrap_or(0.0);
        let vc = m.get("vote_count").and_then(|v| v.parse().ok()).unwrap_or(0);

        let empty = HashMap::new();
        let c = credits.get(i).unwrap_or(&empty);

        films.push(Film {
            title: m.get("title").cloned().unwrap_or_default(),
            genres: pipe_names(m.get("genres").unwrap_or(&"[]".to_string())),
            plot_keywords: pipe_names(m.get("keywords").unwrap_or(&"[]".to_string())),
            director: get_director(c.get("crew").unwrap_or(&"[]".to_string())),
            actor1: get_cast_member(c.get("cast").unwrap_or(&"[]".to_string()), 0),
            actor2: get_cast_member(c.get("cast").unwrap_or(&"[]".to_string()), 1),
            actor3: get_cast_member(c.get("cast").unwrap_or(&"[]".to_string()), 2),
            year,
            vote_average: va,
            vote_count: vc,
        });
    }
    films
}

fn get_features(film: &Film) -> Vec<String> {
    let mut features = Vec::new();
    if !film.director.is_empty() { features.push(film.director.clone()); }
    for a in [&film.actor1, &film.actor2, &film.actor3] {
        if !a.is_empty() { features.push(a.clone()); }
    }
    if !film.plot_keywords.is_empty() {
        for kw in film.plot_keywords.split('|') {
            if !kw.is_empty() { features.push(kw.to_string()); }
        }
    }
    if !film.genres.is_empty() {
        for g in film.genres.split('|') {
            if !g.is_empty() { features.push(g.to_string()); }
        }
    }
    features
}

fn euclidean(a: &[i32], b: &[i32]) -> f64 {
    let sum: i64 = a.iter().zip(b.iter()).map(|(x, y)| {
        let d = (*x as i64) - (*y as i64);
        d * d
    }).sum();
    (sum as f64).sqrt()
}

fn find_neighbors(films: &[Film], target_idx: usize, n: usize) -> Vec<usize> {
    let target_features = get_features(&films[target_idx]);
    let mut all_genres = HashSet::new();
    for f in films {
        if !f.genres.is_empty() {
            for g in f.genres.split('|') { all_genres.insert(g.to_string()); }
        }
    }

    let mut feature_set: HashSet<String> = target_features.iter().cloned().collect();
    feature_set.extend(all_genres);
    let feature_list: Vec<String> = feature_set.into_iter().collect();

    let vectors: Vec<Vec<i32>> = films.iter().map(|film| {
        let film_feats: HashSet<String> = get_features(film).into_iter().collect();
        feature_list.iter().map(|f| if film_feats.contains(f) { 1 } else { 0 }).collect()
    }).collect();

    let target_vec = &vectors[target_idx];
    let mut dists: Vec<(usize, f64)> = vectors.iter().enumerate()
        .map(|(i, v)| (i, euclidean(target_vec, v)))
        .collect();
    dists.sort_by(|a, b| a.1.partial_cmp(&b.1).unwrap());

    dists.iter().take(n).map(|(idx, _)| *idx).collect()
}

fn gaussian(x: f64, y: f64, sigma: f64) -> f64 {
    if sigma == 0.0 { return 0.0; }
    (-(x - y).powi(2) / (2.0 * sigma * sigma)).exp()
}

fn levenshtein(a: &str, b: &str) -> usize {
    let a: Vec<char> = a.chars().collect();
    let b: Vec<char> = b.chars().collect();
    let la = a.len();
    let lb = b.len();
    let mut d = vec![vec![0usize; lb + 1]; la + 1];
    for i in 0..=la { d[i][0] = i; }
    for j in 0..=lb { d[0][j] = j; }
    for i in 1..=la {
        for j in 1..=lb {
            let cost = if a[i - 1] == b[j - 1] { 0 } else { 1 };
            d[i][j] = (d[i - 1][j] + 1).min(d[i][j - 1] + 1).min(d[i - 1][j - 1] + cost);
        }
    }
    d[la][lb]
}

fn fuzzy_ratio(a: &str, b: &str) -> i32 {
    let al = a.to_lowercase();
    let bl = b.to_lowercase();
    let max_len = al.len().max(bl.len());
    if max_len == 0 { return 100; }
    let dist = levenshtein(&al, &bl);
    ((max_len - dist) as f64 / max_len as f64 * 100.0) as i32
}

fn is_sequel(t1: &str, t2: &str) -> bool {
    fuzzy_ratio(t1, t2) > 50
}

fn recommend(films: &[Film], target_idx: usize, dedup_sequels: bool) -> Vec<Candidate> {
    let neighbor_idxs = find_neighbors(films, target_idx, 31);

    let mut max_votes = 0;
    let mut candidates: Vec<Candidate> = Vec::new();
    for &idx in &neighbor_idxs {
        let f = &films[idx];
        if f.vote_count > max_votes { max_votes = f.vote_count; }
        candidates.push(Candidate {
            title: f.title.clone(),
            year: f.year,
            score: f.vote_average,
            votes: f.vote_count,
            index: idx,
            rank_score: 0.0,
        });
    }

    let main_title = candidates[0].title.clone();
    let main_year = candidates[0].year as f64;

    for c in candidates.iter_mut() {
        if is_sequel(&main_title, &c.title) {
            c.rank_score = 0.0;
            continue;
        }
        let fact1 = if main_year > 0.0 && c.year > 0 {
            gaussian(main_year, c.year as f64, 20.0)
        } else { 1.0 };
        let fact2 = if max_votes > 0 {
            gaussian(c.votes as f64, max_votes as f64, max_votes as f64)
        } else { 0.0 };
        c.rank_score = c.score * c.score * fact1 * fact2;
    }

    candidates.sort_by(|a, b| b.rank_score.partial_cmp(&a.rank_score).unwrap());

    let mut selected: Vec<Candidate> = Vec::new();
    for c in &candidates {
        if selected.len() >= 5 { break; }
        let dominated = selected.iter().any(|s| s.title == c.title || is_sequel(&c.title, &s.title));
        if !dominated { selected.push(c.clone()); }
    }

    if dedup_sequels {
        let mut remove: HashSet<String> = HashSet::new();
        for i in 0..selected.len() {
            for j in (i + 1)..selected.len() {
                if is_sequel(&selected[i].title, &selected[j].title) {
                    let drop = if selected[i].year < selected[j].year {
                        &selected[j].title
                    } else {
                        &selected[i].title
                    };
                    remove.insert(drop.clone());
                }
            }
        }
        selected.retain(|s| !remove.contains(&s.title));

        for c in &candidates {
            if selected.len() >= 5 { break; }
            let dominated = selected.iter().any(|s| s.title == c.title || is_sequel(&c.title, &s.title));
            if !dominated { selected.push(c.clone()); }
        }
    }

    selected.truncate(5);
    selected
}

fn find_by_title(films: &[Film], query: &str) -> i32 {
    let q = query.trim().to_lowercase();
    for (i, f) in films.iter().enumerate() {
        if f.title.trim().to_lowercase() == q { return i as i32; }
    }
    let mut best_idx: i32 = -1;
    let mut best_score = 0;
    for (i, f) in films.iter().enumerate() {
        let score = fuzzy_ratio(&q, &f.title.to_lowercase());
        if score > best_score { best_score = score; best_idx = i as i32; }
    }
    if best_score >= 60 { best_idx } else { -1 }
}
