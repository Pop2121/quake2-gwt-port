/*
Copyright (C) 1997-2001 Id Software, Inc.

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.

*/
/* Modifications
   Copyright 2003-2004 Bytonic Software
   Copyright 2010 Google Inc.
*/
package com.googlecode.gwtquake.shared.render;

import com.googlecode.gwtquake.shared.common.Com;
import com.googlecode.gwtquake.shared.common.Constants;
import com.googlecode.gwtquake.shared.common.Globals;
import com.googlecode.gwtquake.shared.common.QuakeImage;
import com.googlecode.gwtquake.shared.util.Math3D;
import com.googlecode.gwtquake.shared.util.Vec3Cache;



/**
 * Warp
 *  
 * @author cwei
 */
public class SkyBox  {
	/**
	 * BoundPoly
	 * @param numverts
	 * @param verts
	 * @param mins
	 * @param maxs
	 */
	static void BoundPoly(int numverts, float[][] verts, float[] mins, float[] maxs) {
		mins[0] = mins[1] = mins[2] = 9999;
		maxs[0] = maxs[1] = maxs[2] = -9999;

		int j;
		float[] v;
		for (int i=0 ; i<numverts ; i++) {
			v = verts[i];
			for (j=0 ; j<3 ; j++) {
				if (v[j] < mins[j])
					mins[j] = v[j];
				if (v[j] > maxs[j])
					maxs[j] = v[j];
			}
		}
	}

	/**
	 * SubdividePolygon
	 * @param numverts
	 * @param verts
	 */
	static void SubdividePolygon(int numverts, float[][] verts)
	{
		int i, j, k;
		float	m;
		float[][] front = new float[64][3];
		float[][] back = new float[64][3];

		int f, b;
		float[] dist = new float[64];
		float	frac;

		if (numverts > 60)
			Com.Error(Constants.ERR_DROP, "numverts = " + numverts);

		float[] mins = Vec3Cache.get();
		float[] maxs = Vec3Cache.get();

		BoundPoly(numverts, verts, mins, maxs);
		float[] v;
		// x,y und z 
		for (i=0 ; i<3 ; i++)
		{
			m = (mins[i] + maxs[i]) * 0.5f;
			m = GlConstants.SUBDIVIDE_SIZE * (float)Math.floor(m / GlConstants.SUBDIVIDE_SIZE + 0.5f);
			if (maxs[i] - m < 8)
				continue;
			if (m - mins[i] < 8)
				continue;

			// cut it
			for (j=0 ; j<numverts ; j++) {
				dist[j] = verts[j][i] - m;
			}

			// wrap cases
			dist[j] = dist[0];

			Math3D.VectorCopy(verts[0], verts[numverts]);
			
			f = b = 0;
			for (j=0 ; j<numverts ; j++)
			{
				v = verts[j];
				if (dist[j] >= 0)
				{
					Math3D.VectorCopy(v, front[f]);
					f++;
				}
				if (dist[j] <= 0)
				{
					Math3D.VectorCopy(v, back[b]);
					b++;
				}
				if (dist[j] == 0 || dist[j+1] == 0) continue;
				
				if ( (dist[j] > 0) != (dist[j+1] > 0) )
				{
					// clip point
					frac = dist[j] / (dist[j] - dist[j+1]);
					for (k=0 ; k<3 ; k++)
						front[f][k] = back[b][k] = v[k] + frac*(verts[j+1][k] - v[k]);
						
					f++;
					b++;
				}
			}

			SubdividePolygon(f, front);
			SubdividePolygon(b, back);
			
			Vec3Cache.release(2); // mins, maxs
			return;
		}
		
		Vec3Cache.release(2); // mins, maxs
		
		// add a point in the center to help keep warp valid
		
		// wird im Konstruktor erschlagen
		// poly = Hunk_Alloc (sizeof(glpoly_t) + ((numverts-4)+2) * VERTEXSIZE*sizeof(float));

		// init polys
		Polygon poly = Polygons.create(numverts + 2);

		poly.next = GlState.warpface.polys;
		GlState.warpface.polys = poly;
		
		float[] total = Vec3Cache.get();
		Math3D.VectorClear(total);
		float total_s = 0;
		float total_t = 0;
		float s, t;
		for (i = 0; i < numverts; i++) {
            poly.setX(i + 1, verts[i][0]);
            poly.setY(i + 1, verts[i][1]);
            poly.setZ(i + 1, verts[i][2]);
            s = Math3D.DotProduct(verts[i], GlState.warpface.texinfo.vecs[0]);
            t = Math3D.DotProduct(verts[i], GlState.warpface.texinfo.vecs[1]);

            total_s += s;
            total_t += t;
            Math3D.VectorAdd(total, verts[i], total);

            poly.setS1(i + 1, s);
            poly.setT1(i + 1, t);
        }
        
        float scale = 1.0f / numverts; 
        poly.setX(0, total[0] * scale);
        poly.setY(0, total[1] * scale);
        poly.setZ(0, total[2] * scale);
        poly.setS1(0, total_s * scale);
        poly.setT1(0, total_t * scale);

        poly.setX(i + 1, poly.getX(1));
        poly.setY(i + 1, poly.getY(1));
        poly.setZ(i + 1, poly.getZ(1));
        poly.setS1(i + 1, poly.getS1(1));
        poly.setT1(i + 1, poly.getT1(1));
        poly.setS2(i + 1, poly.getS2(1));
        poly.setT2(i + 1, poly.getT2(1));
        
        Vec3Cache.release(); // total
	}

	private static  final float[][] tmpVerts = new float[64][3];

	/**
	 * GL_SubdivideSurface
	 * Breaks a polygon up along axial 64 unit
	 * boundaries so that turbulent and sky warps
	 * can be done reasonably.
	 */
    static void GL_SubdivideSurface(Surface fa) {
        float[][] verts = tmpVerts;
        float[] vec;
        GlState.warpface = fa;
        //
        // convert edges back to a normal polygon
        //
        int numverts = 0;
        for (int i = 0; i < fa.numedges; i++) {
            int lindex = Models.loadmodel.surfedges[fa.firstedge + i];

            if (lindex > 0)
                vec = Models.loadmodel.vertexes[Models.loadmodel.edges[lindex].v[0]].position;
            else
                vec = Models.loadmodel.vertexes[Models.loadmodel.edges[-lindex].v[1]].position;
            Math3D.VectorCopy(vec, verts[numverts]);
            numverts++;
        }
        SubdividePolygon(numverts, verts);
    }



//	  ===================================================================

	static float[][] skyclip = {
		{ 1,  1, 0},
		{ 1, -1, 0},
		{ 0, -1, 1},
		{ 0,  1, 1},
		{ 1,  0, 1},
		{-1,  0, 1} 
	};

	static int c_sky;

	// 1 = s, 2 = t, 3 = 2048
	static int[][]	st_to_vec =
	{
		{3,-1,2},
		{-3,1,2},

		{1,3,2},
		{-1,-3,2},

		{-2,-1,3},		// 0 degrees yaw, look straight up
		{2,-1,-3}		// look straight down

	};

	static int[][]	vec_to_st =
	{
		{-2,3,1},
		{2,3,-1},

		{1,3,2},
		{-1,3,-2},

		{-2,-1,3},
		{-2,1,-3}

	};

	static float[][] skymins = new float[2][6];
	static float[][] skymaxs = new float[2][6];
	static float	sky_min, sky_max;

	// stack variable
	static  float[] v = {0, 0, 0};
	static  float[] av = {0, 0, 0};
	/**
	 * DrawSkyPolygon
	 * @param nump
	 * @param vecs
	 */
	static void DrawSkyPolygon(int nump, float[][] vecs)
	{
		c_sky++;
		// decide which face it maps to
		Math3D.VectorCopy(Globals.vec3_origin, v);
		int i, axis;
		for (i=0; i<nump ; i++)
		{
			Math3D.VectorAdd(vecs[i], v, v);
		}
		av[0] = Math.abs(v[0]);
		av[1] = Math.abs(v[1]);
		av[2] = Math.abs(v[2]);
		if (av[0] > av[1] && av[0] > av[2])
		{
			if (v[0] < 0)
				axis = 1;
			else
				axis = 0;
		}
		else if (av[1] > av[2] && av[1] > av[0])
		{
			if (v[1] < 0)
				axis = 3;
			else
				axis = 2;
		}
		else
		{
			if (v[2] < 0)
				axis = 5;
			else
				axis = 4;
		}

		// project new texture coords
		float	s, t, dv;
		int j;
		for (i=0 ; i<nump ; i++)
		{
			j = vec_to_st[axis][2];
			if (j > 0)
				dv = vecs[i][j - 1];
			else
				dv = -vecs[i][-j - 1];
			if (dv < 0.001f)
				continue;	// don't divide by zero
			j = vec_to_st[axis][0];
			if (j < 0)
				s = -vecs[i][-j -1] / dv;
			else
				s = vecs[i][j-1] / dv;
			j = vec_to_st[axis][1];
			if (j < 0)
				t = -vecs[i][-j -1] / dv;
			else
				t = vecs[i][j-1] / dv;

			if (s < skymins[0][axis])
				skymins[0][axis] = s;
			if (t < skymins[1][axis])
				skymins[1][axis] = t;
			if (s > skymaxs[0][axis])
				skymaxs[0][axis] = s;
			if (t > skymaxs[1][axis])
				skymaxs[1][axis] = t;
		}
	}

	static final int SIDE_BACK = 1;
	static final int SIDE_FRONT = 0;
	static final int SIDE_ON = 2;
	
	static int[] sides = new int[GlConstants.MAX_CLIP_VERTS];
	static float[][][][] newv = new float[6][2][GlConstants.MAX_CLIP_VERTS][3];

	/**
	 * ClipSkyPolygon
	 * @param nump
	 * @param vecs
	 * @param stage
	 */
	static void ClipSkyPolygon(int nump, float[][] vecs, int stage)
	{
		if (nump > GlConstants.MAX_CLIP_VERTS-2)
			Com.Error(Constants.ERR_DROP, "ClipSkyPolygon: MAX_CLIP_VERTS");
		if (stage == 6)
		{	// fully clipped, so draw it
			DrawSkyPolygon(nump, vecs);
			return;
		}

		boolean front = false;
		boolean back = false;
		float[] norm = skyclip[stage];

		int i;
		float d;
		for (i=0 ; i<nump ; i++)
		{
			d = Math3D.DotProduct(vecs[i], norm);
			if (d > GlConstants.ON_EPSILON)
			{
				front = true;
				sides[i] = SIDE_FRONT;
			}
			else if (d < -GlConstants.ON_EPSILON)
			{
				back = true;
				sides[i] = SIDE_BACK;
			}
			else
				sides[i] = SIDE_ON;
			GlState.dists[i] = d;
		}

		if (!front || !back)
		{	// not clipped
			ClipSkyPolygon (nump, vecs, stage+1);
			return;
		}

		// clip it
		sides[i] = sides[0];
		GlState.dists[i] = GlState.dists[0];
		Math3D.VectorCopy(vecs[0], vecs[i]);

		int newc0 = 0; 	int  newc1 = 0;
		float[] v;
		float e;
		int j;
		for (i=0; i<nump ; i++)
		{
			v = vecs[i];
			switch (sides[i])
			{
			case SIDE_FRONT:
				Math3D.VectorCopy(v, newv[stage][0][newc0]);
				newc0++;
				break;
			case SIDE_BACK:
				Math3D.VectorCopy(v, newv[stage][1][newc1]);
				newc1++;
				break;
			case SIDE_ON:
				Math3D.VectorCopy(v, newv[stage][0][newc0]);
				newc0++;
				Math3D.VectorCopy (v, newv[stage][1][newc1]);
				newc1++;
				break;
			}

			if (sides[i] == SIDE_ON || sides[i+1] == SIDE_ON || sides[i+1] == sides[i])
				continue;

			d = GlState.dists[i] / (GlState.dists[i] - GlState.dists[i+1]);
			for (j=0 ; j<3 ; j++)
			{
				e = v[j] + d * (vecs[i + 1][j] - v[j]);
				newv[stage][0][newc0][j] = e;
				newv[stage][1][newc1][j] = e;
			}
			newc0++;
			newc1++;
		}

		// continue
		ClipSkyPolygon(newc0, newv[stage][0], stage+1);
		ClipSkyPolygon(newc1, newv[stage][1], stage+1);
	}

	static float[][] verts = new float[GlConstants.MAX_CLIP_VERTS][3];

	/**
	 * R_AddSkySurface
	 */
	static void R_AddSkySurface(Surface fa)
	{
	    // calculate vertex values for sky box
        for (Polygon p = fa.polys; p != null; p = p.next) {
            for (int i = 0; i < p.numverts; i++) {
                verts[i][0] = p.getX(i) - GlState.r_origin[0];
                verts[i][1] = p.getY(i) - GlState.r_origin[1];
                verts[i][2] = p.getZ(i) - GlState.r_origin[2];
            }
            ClipSkyPolygon(p.numverts, verts, 0);
        }
 	}

	/**
	 * R_ClearSkyBox
	 */
	static void R_ClearSkyBox()
	{
		float[] skymins0 = skymins[0];
		float[] skymins1 = skymins[1];
		float[] skymaxs0 = skymaxs[0];
		float[] skymaxs1 = skymaxs[1];
		
		for (int i=0 ; i<6 ; i++)
		{
			skymins0[i] = skymins1[i] = 9999;
			skymaxs0[i] = skymaxs1[i] = -9999;
		}
	}
	
	
	// stack variable
	static float[] v1 = {0, 0, 0};
	static float[] b = {0, 0, 0};
	/**
	 * MakeSkyVec
	 * @param s
	 * @param t
	 * @param axis
	 */
	static void MakeSkyVec(float s, float t, int axis)
	{
		b[0] = s*2300;
		b[1] = t*2300;
		b[2] = 2300;

		int j, k;
		for (j=0 ; j<3 ; j++)
		{
			k = st_to_vec[axis][j];
			if (k < 0)
				v1[j] = -b[-k - 1];
			else
				v1[j] = b[k - 1];
		}

		// avoid bilerp seam
		s = (s + 1) * 0.5f;
		t = (t + 1) * 0.5f;

		if (s < sky_min)
			s = sky_min;
		else if (s > sky_max)
			s = sky_max;
		if (t < sky_min)
			t = sky_min;
		else if (t > sky_max)
			t = sky_max;

		t = 1.0f - t;
		GlState.gl.glTexCoord2f (s, t);
		GlState.gl.glVertex3f(v1[0], v1[1], v1[2]);
	}

	static int[] skytexorder = {0,2,1,3,4,5};

	/**
	 * R_DrawSkyBox
	 */
	static void R_DrawSkyBox()
	{
		int i;
		
		if (GlState.skyrotate != 0)
		{	// check for no sky at all
			for (i=0 ; i<6 ; i++)
				if (skymins[0][i] < skymaxs[0][i]
				&& skymins[1][i] < skymaxs[1][i])
					break;
			if (i == 6)
				return;		// nothing visible
		}
		

		GlState.gl.glPushMatrix ();
		GlState.gl.glTranslatef (GlState.r_origin[0], GlState.r_origin[1], GlState.r_origin[2]);
		GlState.gl.glRotatef (GlState.r_newrefdef.time * GlState.skyrotate, GlState.skyaxis[0], GlState.skyaxis[1], GlState.skyaxis[2]);
		
		for (i=0 ; i<6 ; i++)
		{
			if (GlState.skyrotate != 0)
			{	// hack, forces full sky to draw when rotating
				skymins[0][i] = -1;
				skymins[1][i] = -1;
				skymaxs[0][i] = 1;
				skymaxs[1][i] = 1;
			}

			if (skymins[0][i] >= skymaxs[0][i]
			|| skymins[1][i] >= skymaxs[1][i])
				continue;

			Images.GL_Bind(GlState.sky_images[skytexorder[i]].texnum);
			
			GlState.gl.glBegin(Gl1Context._GL_QUADS);
			MakeSkyVec(skymins[0][i], skymins[1][i], i);
			MakeSkyVec(skymins[0][i], skymaxs[1][i], i);
			MakeSkyVec(skymaxs[0][i], skymaxs[1][i], i);
			MakeSkyVec(skymaxs[0][i], skymins[1][i], i);
			GlState.gl.glEnd ();
		}
		GlState.gl.glPopMatrix ();
	}

	// 3dstudio environment map names
	static String[] suf = {"rt", "bk", "lf", "ft", "up", "dn"};
	
	/**
	 * R_SetSky
	 * @param name
	 * @param rotate
	 * @param axis
	 */
	static void R_SetSky(String name, float rotate, float[] axis)
	{
		assert (axis.length == 3) : "vec3_t bug";
		String pathname;
		GlState.skyname = name;

		GlState.skyrotate = rotate;
		Math3D.VectorCopy(axis, GlState.skyaxis);

		for (int i=0 ; i<6 ; i++)
		{
			// chop down rotating skies for less memory
			if (GlState.gl_skymip.value != 0 || GlState.skyrotate != 0)
				GlState.gl_picmip.value++;

			// Com_sprintf (pathname, sizeof(pathname), "env/%s%s.tga", skyname, suf[i]);
			pathname = "env/" + GlState.skyname + suf[i] + ".tga";

//			gl.log("loadSky:" + pathname);
			
			GlState.sky_images[i] = Images.findTexture(pathname, QuakeImage.it_sky);

			if (GlState.sky_images[i] == null)
				GlState.sky_images[i] = GlState.r_notexture;

			if (GlState.gl_skymip.value != 0 || GlState.skyrotate != 0)
			{	// take less memory
				GlState.gl_picmip.value--;
				sky_min = 1.0f / 256;
				sky_max = 255.0f / 256;
			}
			else	
			{
				sky_min = 1.0f / 512;
				sky_max = 511.0f / 512;
			}
		}
	}
}