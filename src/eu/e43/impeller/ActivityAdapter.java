/* Copyright 2013 Owen Shepherd. A part of Impeller.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 *     
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.e43.impeller;

import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.text.Html;
import android.util.Log;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Checkable;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

public class ActivityAdapter extends BaseAdapter {
	static final String TAG = "ActivityAdapter";
	
    Cursor                      m_cursor;
	ActivityWithAccount		    m_ctx;
    LruCache<String, Integer>   m_objectPositions;

	public ActivityAdapter(ActivityWithAccount ctx) {
		m_cursor = null;
		m_ctx  = ctx;
        m_objectPositions = new LruCache<String, Integer>(20);
	}

    public int findItemById(String id) {
        Integer pos = m_objectPositions.get(id);
        if(pos == null) {
            if(m_cursor == null) return -1;

            if(!m_cursor.moveToFirst()) return -1;
            do {
                String objId = m_cursor.getString(1);
                if(id.equals(objId))
                    return m_cursor.getPosition();
            } while(m_cursor.moveToNext());
            return -1;
        } else return pos;
    }

    private static class Wrapper extends FrameLayout implements Checkable {
        private boolean m_checked = false;

        public Wrapper(View child) {
            super(child.getContext());
            addView(child);
        }

        @Override
        public void setChecked(boolean b) {
            m_checked = b;

            if(b) {
                setBackgroundResource(android.R.color.holo_blue_bright);
            } else {
                setBackgroundResource(android.R.color.transparent);
            }
        }

        @Override
        public boolean isChecked() {
            return m_checked;
        }

        @Override
        public void toggle() {
            setChecked(!m_checked);
        }
    }

    public void updateCursor(Cursor c) {
        if(m_cursor != null && m_cursor != c) m_cursor.close();
        m_cursor = c;
        notifyDataSetChanged();
        m_objectPositions.evictAll();
    }
	
	public void close() {
        if(m_cursor != null)
            m_cursor.close();
		m_cursor = null;
	}

	@Override
	public int getCount() {
        if(m_cursor != null)
		    return m_cursor.getCount();
        else
            return 0;
	}

	@Override
	public Object getItem(int position) {
        try {
		    m_cursor.moveToPosition(position);
            JSONObject act = new JSONObject(m_cursor.getString(0));
            JSONObject obj = act.optJSONObject("object");
            if(obj != null) {
                String id = obj.optString("id");
                if(id != null) {
                    m_objectPositions.put(id, position);
                }
            }

            act.put("_replies", m_cursor.getInt(1));
            act.put("_likes",   m_cursor.getInt(2));
            act.put("_shares",  m_cursor.getInt(3));

            return act;
        } catch(JSONException e) {
            throw new RuntimeException(e);
        }
	}

	private static String getImage(JSONObject obj) {
		JSONObject mediaLink = obj.optJSONObject("image");
		if(mediaLink == null) return null;
		
		return Utils.getImageUrl(mediaLink);
	}
	
	@Override
	public int getViewTypeCount()
	{
		return 3;
	}
	
	@Override
	public int getItemViewType(int pos)
	{
		JSONObject json = (JSONObject) getItem(pos);
		return getItemViewType(json);
	}
	
	public int getItemViewType(JSONObject act) {
		JSONObject obj = act.optJSONObject("object");
		if(obj == null) {
			return 0;
		} else if("image".equals(obj.optString("objectType"))) {
			return 2;
		} else if("note".equals(obj.optString("objectType"))) {
			return 1;
		} else {
			return 0;
		}
	}
	
	@Override
	public View getView(int position, View v, ViewGroup parent) {
	    JSONObject json = (JSONObject) getItem(position);
	    int type = getItemViewType(json);

	    switch(type) {
	    case 0:
	    	// Simple activity
	    	if(v == null) {
	    		LayoutInflater vi = LayoutInflater.from(m_ctx);
	    		v = new Wrapper(vi.inflate(android.R.layout.simple_list_item_1, null));
	    	}
	    	
	    	TextView text = (TextView) v.findViewById(android.R.id.text1);
	    	text.setText(Html.fromHtml(json.optJSONObject("object").optString("content", "(Item with missing summary)")));
	    	break;
	    	
	    case 1:
	    	// Note
		    if (v == null) {
		        LayoutInflater vi = LayoutInflater.from(m_ctx);
		        v = new Wrapper(vi.inflate(R.layout.post_view, null));
		    }

		    TextView   caption    = (TextView)  v.findViewById(R.id.caption);
		    TextView  description = (TextView)  v.findViewById(R.id.description);
		    ImageView image       = (ImageView) v.findViewById(R.id.image);
            ImageView originImage = (ImageView) v.findViewById(R.id.originImage);

			description.setText(Html.fromHtml(json.optString("content", "(Action string missing)")));
		    try {
		    	JSONObject obj = json.getJSONObject("object");
		    	String content = obj.getString("content");
		    	
		    	PumpHtml.setFromHtml(m_ctx, caption, content);
				
				m_ctx.getImageLoader().setImage(image, getImage(json.getJSONObject("actor")));

                String actorId  = json.getJSONObject("actor").getString("id");
                String authorId = json.getJSONObject("object").getJSONObject("author").getString("id");
                if(actorId.equals(authorId)) {
                    originImage.setVisibility(View.GONE);
                } else {
                    originImage.setVisibility(View.VISIBLE);
                    m_ctx.getImageLoader().setImage(originImage, getImage(json.getJSONObject("object").getJSONObject("author")));
                }
			} catch (JSONException e) {
				caption.setText(Html.fromHtml(e.getLocalizedMessage()));
				//caption.loadData(e.getLocalizedMessage(), "text/plain", "utf-8");
			}
		    break;
		    
	    case 2:
	    	// Image
	    	if(v == null) {
	    		LayoutInflater vi = LayoutInflater.from(m_ctx);
	    		v = new Wrapper(vi.inflate(R.layout.image_view, null));
	    	}
	    	
	    	TextView imgDescription = (TextView)  v.findViewById(R.id.description);
	    	ImageView imgImg        = (ImageView) v.findViewById(R.id.imageImage);
	    	
	    	try {
	    		imgDescription.setText(Html.fromHtml(json.optString("content", "(Action string missing)")));
	    		m_ctx.getImageLoader().setImage(imgImg, getImage(json.getJSONObject("object")));
	    	} catch(JSONException e) {
	    		imgDescription.setText(e.getMessage());
	    	}
	    	break;
	    }

        if(type == 1 || type == 2) {
            int replies = json.optInt("_replies");
            int likes   = json.optInt("_likes");
            int shares  = json.optInt("_shares");
            Utils.updateStatebar(v, replies, likes, shares);
        }
		
		return v;
	}

	@Override
	public long getItemId(int id) {
		return id;
	}
}
