package maddie.practice.popularmoviesstage1;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Fragment holding GridView of movies returned from The Movie Database API
 */
public class MoviesFragment extends Fragment {

    private final String LOG_TAG = MoviesFragment.class.getSimpleName();

    private MovieArrayAdapter mAdapter;
    private GridView mMovieGrid;
    private View mPageLoading;

    public MoviesFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        SharedPreferences prefs= PreferenceManager.getDefaultSharedPreferences(getActivity());

        prefs.registerOnSharedPreferenceChangeListener(
            new SharedPreferences
                .OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                    mAdapter.clear();
                    updateMovies();
                }
            });
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
        Bundle savedInstanceState) {

        mAdapter = new MovieArrayAdapter(getContext());

        View rootView = inflater.inflate(R.layout.fragment_movies, container, false);

        mPageLoading = rootView.findViewById(R.id.page_loading);
        mPageLoading.bringToFront();

        // Get a reference to the ListView, and attach this adapter to it.
        mMovieGrid = (GridView) rootView.findViewById(R.id.gridview_movies);
        mMovieGrid.setAdapter(mAdapter);
        mMovieGrid.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {
                Movie movie = mAdapter.getItem(position);
                Intent intent = new Intent(getActivity(), MovieDetailActivity.class)
                    .putExtra("movie Id", movie.getId());
                startActivity(intent);
            }
        });

        return rootView;
    }

    @Override
    public void onStart() {
        super.onStart();
        updateMovies();
    }


    public void updateMovies() {
        FetchMoviesTask moviesTask = new FetchMoviesTask();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        String sortPref = prefs.getString(getString(R.string.pref_sort_key),
            getString(R.string.pref_sort_default));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            moviesTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, sortPref);
        } else {
            moviesTask.execute(sortPref);
        }
    }


    public class FetchMoviesTask extends AsyncTask<String, Void, Movie[]> {

        private final String LOG_TAG = FetchMoviesTask.class.getSimpleName();
        /**
         * Take the String representing the complete forecast in JSON Format and pull out the data we need to construct the Strings needed
         * for the wireframes.
         *
         * Fortunately parsing is easy:  constructor takes the JSON string and converts it into an Object hierarchy for us.
         */
        private Movie[] getMoviesFromJson(String moviesJsonStr)
            throws JSONException {

            // These are the names of the JSON objects that need to be extracted.
            final String MDB_RESULTS = "results";
            final String MDB_ID = "id";
            final String MDB_POPULARITY = "popularity";
            final String MDB_RATING = "vote_average";
            final String MDB_POSTER_PATH = "poster_path";

            JSONObject moviesJson = new JSONObject(moviesJsonStr);
            JSONArray moviesJsonArray = moviesJson.getJSONArray(MDB_RESULTS);

            Movie[] moviesArray = new Movie[moviesJsonArray.length()];

            SharedPreferences sharedPrefs =
                PreferenceManager.getDefaultSharedPreferences(getActivity());
            String sortOrder = sharedPrefs.getString(
                getString(R.string.pref_sort_key),
                getString(R.string.pref_sort_default));

            for (int i = 0; i < moviesJsonArray.length(); i++) {
                long id;
                String posterPath;
                long popularity;
                double rating;

                // Get the JSON object representing the movie
                JSONObject movie = moviesJsonArray.getJSONObject(i);

                id = movie.getLong(MDB_ID);
                posterPath = movie.getString(MDB_POSTER_PATH);
                popularity = movie.getLong(MDB_POPULARITY);
                rating = movie.getDouble(MDB_RATING);

                Movie currentMovie = new Movie(id, null, posterPath, rating, popularity, null, null);
                moviesArray[i] = currentMovie;

            }

            moviesArray = sortMovies(moviesArray, sortOrder);
            Log.v(LOG_TAG, moviesArray.toString());
            return moviesArray;
        }

        protected Movie[] sortMovies(Movie[] originalMovies, String sortOrder) {

            //TODO take out hard coding
            switch (sortOrder) {
                case "popularity.desc":
                    Arrays.sort(originalMovies, new Comparator<Movie>() {
                        @Override
                        public int compare(Movie lhs, Movie rhs) {
                            if (lhs.getPopularity() > rhs.getPopularity()) {
                                return -1;
                            } else {
                                return 1;
                            }
                        }
                    });
                    break;
                case "vote_average.desc":
                    Arrays.sort(originalMovies, new Comparator<Movie>() {
                        @Override
                        public int compare(Movie lhs, Movie rhs) {
                            if (lhs.getRating() > rhs.getRating()) {
                                return -1;
                            } else {
                                return 1;
                            }
                        }
                    });
                    break;
                default:
                    break;
            }

            return originalMovies;
        }

        @Override
        protected void onPreExecute() {
            mPageLoading.setVisibility(View.VISIBLE);
            mMovieGrid.setClickable(false);
            super.onPreExecute();
        }

        @Override
        protected Movie[] doInBackground(String... params) {

            HttpURLConnection urlConnection = null;
            BufferedReader reader = null;

            // Will contain the raw JSON response as a string.
            String moviesJsonStr = null;

            try {
                final String MOVIES_BASE_URL =
                    "http://api.themoviedb.org/3/discover/movie?";
                final String API_KEY_PARAM = "api_key";
                final String YEAR_PARAM = "year";

                Uri builtUri = Uri.parse(MOVIES_BASE_URL).buildUpon()
                    .appendQueryParameter(API_KEY_PARAM, BuildConfig.MY_MOVIE_DB_API_KEY)
                    .appendQueryParameter(YEAR_PARAM, "2015")
                    .build();

                URL url = new URL(builtUri.toString());

                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");
                urlConnection.connect();

                // Read the input stream into a String
                InputStream inputStream = urlConnection.getInputStream();
                StringBuffer buffer = new StringBuffer();
                if (inputStream == null) {
                    // Nothing to do.
                    return null;
                }
                reader = new BufferedReader(new InputStreamReader(inputStream));

                String line;
                while ((line = reader.readLine()) != null) {
                    buffer.append(line + "\n");
                }

                if (buffer.length() == 0) {
                    // Stream was empty.  No point in parsing.
                    return null;
                }
                moviesJsonStr = buffer.toString();
                Log.v(LOG_TAG, moviesJsonStr);
            } catch (IOException e) {
                Log.e(LOG_TAG, "Error ", e);
                // If the code didn't successfully get the movies data, there's no point in attempting
                // to parse it.
                return null;
            } finally {
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (final IOException e) {
                        Log.e(LOG_TAG, "Error closing stream", e);
                    }
                }
            }

            try {
                return getMoviesFromJson(moviesJsonStr);
            } catch (JSONException e) {
                Log.e(LOG_TAG, e.getMessage(), e);
                e.printStackTrace();
            }

            // This will only happen if there was an error getting or parsing the forecast.
            return null;
        }

        @Override
        protected void onPostExecute(Movie[] result) {
            if (result != null) {
                mAdapter.clear();
                Log.v(LOG_TAG, result.toString());
                for (Movie movie : result) {
                    mAdapter.add(movie);
                }
                mMovieGrid.setAdapter(mAdapter);
            }
            mPageLoading.setVisibility(View.GONE);
            mMovieGrid.setClickable(true);

        }
    }

    public class MovieArrayAdapter extends BaseAdapter {

        ArrayList<Movie> array;

        private Context context;

        MovieArrayAdapter(Context context) {
            this.context = context;
            array = new ArrayList<>();
        }

        @Override
        public int getCount() {
            return array.size();
        }

        @Override
        public Movie getItem(int position) {
            return array.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View gridItem;

            Movie tempMovie = array.get(position);
            if (convertView == null) {
                gridItem = LayoutInflater.from(context).inflate(R.layout.grid_item_movie, parent, false);
            } else {
                gridItem = convertView;
            }
            ImageView moviePoster = (ImageView) gridItem.findViewById(R.id.grid_item_movie_poster);

            int width = mMovieGrid.getMeasuredWidth() / 3;
            moviePoster.setScaleType(ImageView.ScaleType.FIT_CENTER);
            moviePoster.setMinimumWidth(width);
            moviePoster.setMinimumHeight(width);
            moviePoster.setImageBitmap(tempMovie.getPosterBitmap());

            return gridItem;
        }

        public void clear() {
            array.clear();
        }

        public void add(Movie movie) {
            array.add(movie);
        }

    }
}