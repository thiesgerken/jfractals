#ifdef FP64
     #ifdef AMDFP64
        #pragma OPENCL EXTENSION cl_amd_fp64 : enable
    #else
        #pragma OPENCL EXTENSION cl_khr_fp64 : enable
    #endif
    
    typedef double   fp;
    typedef double2  fp2;
    typedef double3  fp3;
    typedef double4  fp4;
    typedef double8  fp8;
    typedef double16 fp16;
#else
    typedef float   fp;
    typedef float2  fp2;
    typedef float3  fp3;
    typedef float4  fp4;
    typedef float8  fp8;
    typedef float16 fp16;
#endif

fp2 cmul (fp2 a, fp2 b) {
	return (fp2) (a.x*b.x-a.y*b.y, a.x*b.y+a.y*b.x);
}

inline fp2 cinv (fp2 z) {
	return (fp2) (z.x, -z.y) / (z.x*z.x+z.y*z.y);
}

inline fp2 cpow (fp2 z, int exp) {
	fp2 result = (fp2) (1.0f,0.0f);

	for ( int i = 0 ; i < abs(exp) ; i ++) 
		result = cmul( result, z);
	
	if (exp<0)
		result = cinv(result);
	
	return result;
}

inline fp2 cdiv (fp2 a, fp2 b) {
	return (fp2) ((a.x*b.x+a.y*b.y)/(b.x*b.x+b.y*b.y), (a.y*b.x-a.x*b.y)/(b.x*b.x+b.y*b.y));
}

inline fp carg (fp2 a) {
   if ( a.x*a.x + a.y*a.y < 1E-5 ) 
		return M_PI/2;

	if(a.x > 0)
        return atan(a.y / a.x);
    else if(a.x < 0 && a.y >= 0)
        return atan(a.y / a.x) + M_PI;
    else if(a.x < 0 && a.y < 0)
        return atan(a.y / a.x) - M_PI;
    else if(a.x == 0 && a.y > 0)
        return M_PI/2;
    else if(a.x == 0 && a.y < 0)
        return -M_PI/2;
    else
        return 0;  
}

inline fp3 hsvtorgb(fp h, fp s, fp v) {
	int hi =  h*3/M_PI;
	fp f =  h*3/M_PI-hi;
	fp p = v * (1-s);
	fp q = v * (1-s*f);
	fp t = v * (1-s*(1-f));

	if ( hi == 1)
		return (fp3) (q, v, p);

	if ( hi == 2) 
		return (fp3) (p, v, t);

	if ( hi == 3) 
		return (fp3) (p, q, v);

	if ( hi == 4) 
		return (fp3) (t, p, v);

	if ( hi == 5) 
		return (fp3) (v, p, q);

	return (fp3) (v, t, p);
}

inline fp2 csin(fp2 z) { 
	return (fp2) (sin(z.x)*cosh(z.y), cos(z.x)*sinh(z.y));
}

inline fp2 ccos(fp2 z) { 
	return (fp2) (cos(z.x)*cosh(z.y), -sin(z.x)*sinh(z.y));
}

inline fp2 cexp(fp2 z) { 
	return exp(z.x) * (fp2) (cos(z.y), sin(z.y));
}


inline fp2 f(fp2 z) {
	return %% F %%;
 // return cinv(z-(fp2)(0,0)) + cpow(z,7) - (fp2) (1,0);
 // return cosc(z);
 // return cpow(z,7) - cpow(z,6) - (fp2) (8, 0);
 //	return cpow(z,3) - (fp2)( 1,0);
}

inline fp2 df(fp2 z) {
 	return %% DF %%;
 // return -cinv(cpow(z-(fp2)(0,0),2))+7*cpow(z,6);
 // return -sinc(z);
 // return 7*cpow(z,6) - 6*cpow(z,5);
 //	return 3*cpow(z,2);
}

kernel void newton ( const int2 size, const fp4 area, const int maxIterations, const fp epsilon, const int2 supersampling, global int* image ) {
	int x = get_global_id(0);
	int y = get_global_id(1);
	
	if ( x >= size.x || y >= size.y)
		return;

	fp3 color = (fp3)0; 
	fp pxCount = supersampling.x*supersampling.y;

	for (int sx = 0; sx < supersampling.x; sx++)
		for (int sy = 0; sy < supersampling.y; sy++) {
		
			fp2 pos = (fp2) (x-0.5+((fp)1/supersampling.x * (sx+0.5)),
					 		 y-0.5+((fp)1/supersampling.y * (sy+0.5)));

			fp2 z = (fp2) (area.x + area.z * pos.x / size.x, area.y + area.w - area.w * pos.y / size.y);
			
			int n = 0;
			fp2 fz = f(z);
	
			while ( n < maxIterations && fz.x*fz.x + fz.y*fz.y > epsilon) {
				z = z - cdiv(fz , df(z));
				fz = f(z);

				n++;
			}

			fp3 subColor = 255*hsvtorgb(fmod((carg(z) + M_PI),2*M_PI), 1-fmod(length(z),10)/10, min((fp) 1, 1 - (fp)(3.5f*n) / maxIterations));

			// BGR vs. RGB	
			color.z += subColor.x / pxCount;
			color.y += subColor.y / pxCount;
			color.x += subColor.z / pxCount;
		}
			
	image[get_global_id(1)*size.x + get_global_id(0)] = (int)color.x + ((int)color.y << 8) + ((int)color.z << 16);
}