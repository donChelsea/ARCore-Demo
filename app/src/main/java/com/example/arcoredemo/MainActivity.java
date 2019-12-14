package com.example.arcoredemo;

import android.graphics.Point;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.google.ar.core.Anchor;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.Scene;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.TransformableNode;

import java.lang.ref.WeakReference;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    ArFragment arFragment;
    private PointerDrawable pointer = new PointerDrawable();
    private boolean isTracking; // indicates if arcore is in tracking state
    private boolean isHitting; // indicates that the user is looking at a plane
    ModelLoader modelLoader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.sceneform_fragment);
        arFragment.getArSceneView().getScene().addOnUpdateListener(new Scene.OnUpdateListener() {
            @Override
            public void onUpdate(FrameTime frameTime) {
                arFragment.onUpdate(frameTime);
                MainActivity.this.onUpdate();
            }
        });

        modelLoader = new ModelLoader(new WeakReference<>(this));

        initalizeGallery();
    }

    // POINTER CREATION

    // if ar core is not tracking, remove the pointer until tracking is restored
    // if yes, checking if ar core is hitting a plane to place pointer
    private void onUpdate() {
        boolean trackingChanged = updateTracking();
        View contentView = findViewById(android.R.id.content);
        if (trackingChanged) {
            if (isTracking) {
                contentView.getOverlay().add(pointer);
            } else {
                contentView.getOverlay().remove(pointer);
            }
            contentView.invalidate();
        }

        if (isTracking) {
            boolean hitTestChanged = updateHitTest();
            if (hitTestChanged) {
                pointer.setEnabled(isHitting);
                contentView.invalidate();
            }
        }
    }

    // uses ar core's camera state and returns true if the tracking state has changed since last call
    private boolean updateTracking() {
        Frame frame = arFragment.getArSceneView().getArFrame();
        boolean wasTracking = isTracking;
        isTracking = frame != null &&
                frame.getCamera().getTrackingState() == TrackingState.TRACKING;
        return isTracking != wasTracking;
    }

    // uses ar core to call frame.hitTest. will return when a hit is detected
    private boolean updateHitTest() {
        Frame frame = arFragment.getArSceneView().getArFrame();
        android.graphics.Point pt = getScreenCenter();
        List<HitResult> hits;
        boolean wasHitting = isHitting;
        isHitting = false;
        if (frame != null) {
            hits = frame.hitTest(pt.x, pt.y);
            for (HitResult hit : hits) {
                Trackable trackable = hit.getTrackable();
                if (trackable instanceof Plane &&
                        ((Plane) trackable).isPoseInPolygon(hit.getHitPose())) {
                    isHitting = true;
                    break;
                }
            }
        }
        return wasHitting != isHitting;
    }

    // helper method to get the center of the screen used in the hit test
    private android.graphics.Point getScreenCenter() {
        View vw = findViewById(android.R.id.content);
        return new android.graphics.Point(vw.getWidth() / 2, vw.getHeight() / 2);
    }

    // SCENE CREATION

    // creates the gallery layout and adds the model to the view
    public void initalizeGallery() {
        LinearLayout gallery = findViewById(R.id.gallery_layout);

        ImageView andy = new ImageView(this);
        andy.setImageResource(R.drawable.droid_thumb);
        andy.setContentDescription("andy");
        andy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addObject(Uri.parse("andy_dance.sfb"));
            }
        });
        gallery.addView(andy);
    }

    // performs the task of adding the model
    private void addObject(Uri model) {
        Frame frame = arFragment.getArSceneView().getArFrame();
        Point point = getScreenCenter();
        List<HitResult> hits;
        if (frame != null) {
            hits = frame.hitTest(point.x, point.y);
            for (HitResult hit: hits) {
                Trackable trackable = hit.getTrackable();
                if (trackable instanceof Plane && ((Plane) trackable).isPoseInPolygon(hit.getHitPose())) {
                    modelLoader.loadModel(hit.createAnchor(), model);
                }
                break;
            }
        }
    }

    // anchor nodes are added to hold the position of the model on the scene
    // transformation nodes adds functionality to model
    public void addNodeToScene(Anchor anchor, ModelRenderable renderable) {
        AnchorNode anchorNode = new AnchorNode(anchor);
        TransformableNode node = new TransformableNode(arFragment.getTransformationSystem());
        node.setRenderable(renderable);
        node.setParent(anchorNode);
        arFragment.getArSceneView().getScene().addChild(anchorNode);
        node.select();
    }

    // will fail if network is down
    public void onException(Throwable throwable){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(throwable.getMessage())
                .setTitle("Error!");
        AlertDialog dialog = builder.create();
        dialog.show();
        return;
    }
}
