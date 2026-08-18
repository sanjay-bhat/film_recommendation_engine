FROM python:3.12-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt \
    && python -c "import nltk; nltk.download('wordnet', quiet=True)"

COPY src/recommend.py src/
COPY dataset/*.zip dataset/
RUN cd dataset && for z in *.zip; do unzip -o "$z" && rm "$z"; done

ENTRYPOINT ["python", "src/recommend.py", "--data-dir", "dataset"]
CMD ["--movie", "The Dark Knight Rises"]
