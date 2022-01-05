package com.example.fluffstroller.pages.walkinprogress;

import android.location.Location;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.example.fluffstroller.R;
import com.example.fluffstroller.databinding.WalkInProgressFragmentBinding;
import com.example.fluffstroller.di.Injectable;
import com.example.fluffstroller.models.UserType;
import com.example.fluffstroller.models.WalkInProgressModel;
import com.example.fluffstroller.services.LocationService;
import com.example.fluffstroller.services.LoggedUserDataService;
import com.example.fluffstroller.services.WalkInProgressService;
import com.example.fluffstroller.utils.FragmentWithServices;
import com.example.fluffstroller.utils.components.EnableLocationPopupDialog;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

public class WalkInProgressPage extends FragmentWithServices implements OnMapReadyCallback {
    private static final String WALK_IN_PROGRESS_PAGE = "WALK_IN_PROGRESS_PAGE";

    @Injectable
    private LoggedUserDataService loggedUserDataService;

    @Injectable
    private LocationService locationService;

    @Injectable
    private WalkInProgressService walkInProgressService;

    private WalkInProgressFragmentBinding binding;
    private GoogleMap googleMap;
    private WalkInProgressViewModel viewModel;
    private Timer timer;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = WalkInProgressFragmentBinding.inflate(inflater, container, false);

        viewModel = new ViewModelProvider(this).get(WalkInProgressViewModel.class);

        if (loggedUserDataService.getLogUserType().equals(UserType.DOG_OWNER)) {
            binding.finishWalkButton.setVisibility(View.GONE);
        } else {
            binding.finishWalkButton.setOnClickListener(view -> {
                Snackbar.make(view, "Finish Walk", Snackbar.LENGTH_SHORT).show();
                locationService.stopRealTimeLocationTracking(getActivity());
                if (timer != null) {
                    timer.cancel();
                }
            });
        }

        viewModel.getLocations().observe(getViewLifecycleOwner(), locations -> {

        });

        viewModel.getDistanceInMeters().observe(getViewLifecycleOwner(), distance -> {
            binding.distanceValueTextView.setText(distance + "");
        });

        viewModel.getElapsedSeconds().observe(getViewLifecycleOwner(), elapsedSeconds -> {
            long minutes = elapsedSeconds / 60;
            long seconds = elapsedSeconds % 60;

            binding.durationValueTextView.setText(minutes + ":" + seconds);
        });

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding.map.onCreate(savedInstanceState);
        binding.map.onResume();
        binding.map.getMapAsync(this);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        if (timer != null) {
            timer.cancel();
        }
        timer = null;
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        this.googleMap = googleMap;
        googleMap.clear();

        locationService.getCurrentLocation(getActivity()).subscribe(locationResponse -> {
            if (locationResponse.hasErrors()) {
                Toast.makeText(requireContext(), "Could not get current location", Toast.LENGTH_SHORT).show();
                return;
            }

            if (locationResponse.data == null) {
                new EnableLocationPopupDialog().show(getChildFragmentManager(), WALK_IN_PROGRESS_PAGE);
                return;
            }

            LatLng latLng = new LatLng(locationResponse.data.latitude, locationResponse.data.longitude);
            this.googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15));
        });

        String walkId = loggedUserDataService.getCurrentWalkId();
        if (!walkId.isEmpty()) {
            walkInProgressService.getWalkInProgressModel(walkId).subscribe(response -> {
                if (response.hasErrors() || response.data == null) {
                    return;
                }
                WalkInProgressModel walkInProgressModel = response.data;

                List<Double> latitudes = walkInProgressModel.getLatitude();
                List<Double> longitudes = walkInProgressModel.getLongitude();

                List<LatLng> points = new ArrayList<>();

                for (int i = 0; i < latitudes.size(); i++) {
                    Double latitude = latitudes.get(i);
                    Double longitude = longitudes.get(i);

                    LatLng latLng = new LatLng(latitude, longitude);

                    points.add(latLng);
                }

                if (points.size() > 0) {
                    PolylineOptions opts = new PolylineOptions()
                            .addAll(points)
                            .color(ContextCompat.getColor(requireContext(), R.color.accent))
                            .width(10);
                    googleMap.addPolyline(opts);
                }

                viewModel.setDistanceInMeters(calculateTotalDistanceInMeters(points));

                long elapsedMillis = System.currentTimeMillis() - walkInProgressModel.getCreationTimeMillis();
                viewModel.setElapsedSeconds(elapsedMillis / 1000L);
                if (timer != null) {
                    timer.cancel();
                }
                timer = new Timer();
                timer.scheduleAtFixedRate(new TimerTask() {
                    @Override
                    public void run() {
                        viewModel.increaseElapsedSeconds(1L);
                    }
                }, 0, 1000);
            });
        }
    }

    private float calculateTotalDistanceInMeters(List<LatLng> points) {
        float totalDistance = 0;

        for (int i = 1; i < points.size(); i++) {
            Location currLocation = new Location("this");
            currLocation.setLatitude(points.get(i).latitude);
            currLocation.setLongitude(points.get(i).longitude);

            Location lastLocation = new Location("this");
            lastLocation.setLatitude(points.get(i - 1).latitude);
            lastLocation.setLongitude(points.get(i - 1).longitude);

            totalDistance += lastLocation.distanceTo(currLocation);
        }

        return totalDistance / 1000.0f;
    }
}