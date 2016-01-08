/*
 *  Copyright 2016 Google Inc. All Rights Reserved.
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.google.blockly;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentManager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.Toast;

import com.google.blockly.control.BlocklyController;
import com.google.blockly.model.BlocklySerializerException;
import com.google.blockly.model.Workspace;
import com.google.blockly.utils.CodeGenerationRequest;
import com.google.blockly.utils.StringOutputStream;

import java.io.FileNotFoundException;

/**
 * Simple base class for a full-screen Blockly Activities class, that uses pull-out navigation
 * drawers for the toolbox.
 */
public abstract class AbstractBlocklyActivity extends AppCompatActivity
        implements NavigationDrawerFragment.NavigationDrawerCallbacks {
    private static final String TAG = "AbstractBlocklyActivity";

    protected ActionBar mActionBar;
    protected NavigationDrawerFragment mNavigationDrawerFragment;
    protected DrawerLayout mDrawerLayout;

    private BlocklyController mController;
    protected WorkspaceFragment mWorkspaceFragment;
    protected ToolboxFragment mToolboxFragment;
    protected TrashFragment mTrashFragment;

    protected CodeGeneratorService mCodeGeneratorService;
    protected boolean mBound = false;

    /**
     * Defines callbacks for service binding, passed to bindService()
     */
    private final ServiceConnection mCodeGenerationConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            CodeGeneratorService.CodeGeneratorBinder binder =
                    (CodeGeneratorService.CodeGeneratorBinder) service;
            mCodeGeneratorService = binder.getService();
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
            mCodeGeneratorService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        onCreateContentView();
        onCreateFragments();
        mNavigationDrawerFragment.setUp(R.id.navigation_drawer, mDrawerLayout,
                onCreateNavigationMenuAdapter());

        mController = onCreateController();
    }

    @NonNull
    protected CharSequence getWorkspaceTitle() {
        return getTitle();
    }

    /**
     * @return the content view layout resource id.
     */
    @NonNull
    protected int getContentViewResId() {
        return R.layout.activity_blockly;
    }

    /**
     * @return The asset path for the xml toolbox config.
     */
    @NonNull
    abstract protected String getWorkspaceToolboxPath();

    /**
     * @return The asset path for the json block definitions.
     */
    @NonNull
    abstract protected String getWorkspaceBlocksPath();

    /**
     * Returns the asset path to the initial workspace to load.  If null, no workspace file will be
     * loaded.
     *
     * @return the asset path to the initial workspace to load.
     */
    @Nullable
    protected String getStartingWorkspacePath() {
        return null;
    }

    /**
     * Returns a generation callback to use for the most recently requested "Run" action.
     * Called from {@link #onRunCode()}.
     *
     * @return The generation callback.
     */
    @NonNull
    abstract protected CodeGenerationRequest.CodeGeneratorCallback
        getCreateCodeGenerationCallback();

    /**
     * Returns the asset file path and name to the generator Javascript to use for the most
     * recently requested "Run" action. Called from {@link #onRunCode()}.
     *
     * @return the asset file path and name to the generator Javascript.
     */
    @NonNull
    abstract protected String getGeneratorJsFilename();

    /**
     * Returns the asset file path and name to the JSON blocks defintion file to use for the most
     * recently requested "Run" action. Called from {@link #onRunCode()}.
     *
     * @return the asset file path and name to the JSON blocks defintion file.
     */
    @NonNull
    abstract protected String getBlockDefinitionsFilename();

    /**
     * Creates or loads the root content view for the Activity.  By default, this is
     * {@code R.layout.activity_blockly}. It is also responsible for extracting and assigning
     * {@link #mActionBar}, {@link #mNavigationDrawerFragment}, and {@link #mDrawerLayout}.
     */
    protected void onCreateContentView() {
        setContentView(getContentViewResId());

        mNavigationDrawerFragment = (NavigationDrawerFragment)
                getSupportFragmentManager().findFragmentById(R.id.navigation_drawer);
        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);

        mActionBar = getSupportActionBar();
        mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
        mActionBar.setDisplayShowTitleEnabled(true);
    }

    /**
     * Creates and returns a {@link ListAdapter} used to populate the navigation menu.  By default,
     * the menu is empty.
     *
     * @return the adapter that populates the navigation menu.
     */
    // TODO: Factor out the navigation menu contents from the AbstractBlocklyActivity.
    @NonNull
    protected ListAdapter onCreateNavigationMenuAdapter() {
        // No menu items.
        return new ArrayAdapter<>(this, 0, 0, new String[]{});
    }

    /**
     * Creates the Views and Fragments before the BlocklyController is constructed.  Override to
     * implement to load a custom View hierarchy.  Responsible for assigning
     * {@link #mWorkspaceFragment}, and optionally, {@link #mToolboxFragment} and
     * {@link #mTrashFragment}. Always called once from {@link #onCreate} and before
     * {@link #onCreateController()}.
     */
    protected void onCreateFragments() {
        FragmentManager fragmentManager = getSupportFragmentManager();

        // Find the workspace.  The workspace container will be empty on the first pass (no fragment
        // will be found), but if the Activity is recreated (such as from an orientation change),
        // the fragment will already exist.
        mWorkspaceFragment = (WorkspaceFragment) fragmentManager
                .findFragmentById(R.id.blockly_workspace_container);
        if (mWorkspaceFragment == null) {
            mWorkspaceFragment = new WorkspaceFragment();
            fragmentManager.beginTransaction()
                    .replace(R.id.blockly_workspace_container, mWorkspaceFragment)
                    .commit();
        }

        mToolboxFragment =
                (ToolboxFragment) getSupportFragmentManager().findFragmentById(R.id.toolbox);
        mTrashFragment = (TrashFragment) getSupportFragmentManager().findFragmentById(R.id.trash);

        // Trash should begin in a closed state.
        getSupportFragmentManager().beginTransaction().hide(mTrashFragment).commit();
    };

    /**
     * Create the BlocklyController if necessary and configures it. This must be called during
     * {@link #onCreate}. It may also be called while the activity is running to reconfigure the
     * controller.
     */
    @NonNull
    protected BlocklyController onCreateController() {
        String toolboxPath = getWorkspaceToolboxPath();
        String blockDefsPath = getWorkspaceBlocksPath();

        BlocklyController.Builder builder = new BlocklyController.Builder(this)
                .setBlocklyStyle(R.style.BlocklyTheme)
                .setAssetManager(getAssets())
                .addBlockDefinitionsFromAsset(blockDefsPath)
                .setToolboxConfigurationAsset(toolboxPath)
                .setWorkspaceFragment(mWorkspaceFragment)
                .setTrashFragment(mTrashFragment)
                .setToolboxFragment(mToolboxFragment, mDrawerLayout)
                .setFragmentManager(getSupportFragmentManager());

        String startingWorkspacePath = getStartingWorkspacePath();
        if (!TextUtils.isEmpty(startingWorkspacePath)) {
            builder.setStartingWorkspaceAsset(startingWorkspacePath);
        }
        return builder.build();
    }

    public final BlocklyController getController() {
        return mController;
    }

    public final Workspace getWorkspace() {
        return mController == null ? null : mController.getWorkspace();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (!mNavigationDrawerFragment.isDrawerOpen()) {
            // Only show items in the action bar relevant to this screen
            // if the drawer is not showing. Otherwise, let the drawer
            // decide what to show in the action bar.
            getMenuInflater().inflate(R.menu.main, menu);
            restoreActionBar();
            return true;
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        } else if (id == R.id.action_save) {
            Workspace workspace = mWorkspaceFragment.getWorkspace();
            try {
                workspace.serializeToXml(openFileOutput("workspace.xml", Context.MODE_PRIVATE));
                Toast.makeText(getApplicationContext(), "Saved workspace contents",
                        Toast.LENGTH_LONG).show();
            } catch (FileNotFoundException | BlocklySerializerException e) {
                Toast.makeText(getApplicationContext(), "Couldn't save workspace.",
                        Toast.LENGTH_LONG).show();
            }
            return true;
        } else if (id == R.id.action_load) {
            Workspace workspace = mWorkspaceFragment.getWorkspace();
            try {
                workspace.loadWorkspaceContents(openFileInput("workspace.xml"));
                workspace.initBlockViews();
            } catch (FileNotFoundException e) {
                Toast.makeText(getApplicationContext(), "Couldn't find saved workspace.",
                        Toast.LENGTH_LONG).show();
            }
            return true;
        } else if (id == R.id.action_clear) {
            mWorkspaceFragment.getWorkspace().resetWorkspace();
            return true;
        } else if (id == R.id.action_airstrike) {  // TODO: Move to DevTestsActivity
            mToolboxFragment.airstrike();
            return true;
        } else if (id == R.id.action_carpet_bomb) {  // TODO: Move to DevTestsActivity
            mToolboxFragment.carpetBomb();
            return true;
        } else if (id == R.id.action_run) {
            onRunCode();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void onRunCode() {
        try {
            if (mBound) {
                final StringOutputStream serialized = new StringOutputStream();
                mWorkspaceFragment.getWorkspace().serializeToXml(serialized);

                mCodeGeneratorService.requestCodeGeneration(
                        new CodeGenerationRequest(serialized.toString(),
                                getCreateCodeGenerationCallback(),
                                getBlockDefinitionsFilename(),
                                getGeneratorJsFilename()));
            }
        } catch (BlocklySerializerException e) {
            Log.wtf(TAG, e);
            Toast.makeText(getApplicationContext(), "Code generation failed.",
                    Toast.LENGTH_LONG).show();

        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Intent intent = new Intent(this, CodeGeneratorService.class);
        bindService(intent, mCodeGenerationConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unbindService(mCodeGenerationConnection);
    }


    public void onNavigationDrawerItemSelected(int position) {
        // Override to handle.
    }

    private void restoreActionBar() {
        ActionBar actionBar = getSupportActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setTitle(getTitle());
    }
}
