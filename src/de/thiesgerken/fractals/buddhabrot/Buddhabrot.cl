#pragma OPENCL EXTENSION cl_khr_int64_base_atomics : enable
#pragma OPENCL EXTENSION cl_khr_int64_extended_atomics : enable

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

inline bool isUsable(const fp2 c, const int minIterations, const int maxIterations, const fp bailoutSquared) {
    fp cy2 = c.y*c.y;
   
    // Quick rejection check if c is in 2nd order period bulb
    if( (c.x+1.0) * (c.x+1.0) + cy2 < 0.0625) return false;

    // Quick rejection check if c is in main cardioid
    fp q = (c.x-0.25)*(c.x-0.25) + cy2;
    if( q*(q+(c.x-0.25)) < 0.25*cy2) return false; 
	
    // test for the smaller bulb left of the period-2 bulb
    if (( ((c.x+1.309)*(c.x+1.309)) + c.y*c.y) < 0.00345) return false;

    // check for the smaller bulbs on top and bottom of the cardioid
    if ((((c.x+0.125)*(c.x+0.125)) + (c.y-0.744)*(c.y-0.744)) < 0.0088) return false;
    if ((((c.x+0.125)*(c.x+0.125)) + (c.y+0.744)*(c.y+0.744)) < 0.0088) return false;

	int n = 0;
	fp2 z = (fp2) (0); 
	
	while (n < maxIterations && z.x*z.x+z.y*z.y < bailoutSquared) {
		fp aux = 2 * z.x * z.y + c.y;
	   	z.x = z.x*z.x - z.y*z.y + c.x;
		z.y = aux;
		n++;
	}

    return n < maxIterations && n >= minIterations;
}

inline fp nextFloat(mwc64x_state_t *rng) {
	#ifdef FP64
		return ((((ulong) MWC64X_NextUint(rng)) << 21)) / (double) ((ulong)1 << 53);
	#else
		return (MWC64X_NextUint(rng) >> 8 ) / (float) (1 << 24);
	#endif
}

kernel void compute(const uint2 seed, int2 size, fp4 area, const int minIterations, const int maxIterations, const fp bailoutSquared, global long* counters) {
	mwc64x_state_t rng;
   
   	rng.x = seed.x; 
    rng.c = seed.y; 
   	
   	// each worker requires two floating point numbers, that results in 4 uints if fp == double and 2 uint if fp == float
   	MWC64X_Skip(&rng, get_global_id(0)*sizeof(fp)/4);

	// pick a random sample (xmin = -2.05, ymin = -1.2, width = 2.65, height = 2.4)
	// fp2 c = (fp2) (-2.05+nextFloat(&rng)*2.65, -1.2+nextFloat(&rng)*2.4); 

	fp2 c = (fp2) (area.x+nextFloat(&rng)*area.z, area.y+nextFloat(&rng)*area.w); 


	if (!isUsable(c, minIterations, maxIterations, bailoutSquared))
		return;
		
	int n = 0;
	fp2 z = (fp2) (0); 
	
	while (n < maxIterations && z.x*z.x+z.y*z.y < bailoutSquared) {
		fp aux = 2 * z.x * z.y + c.y;
	   	z.x = z.x*z.x - z.y*z.y + c.x;
		z.y = aux;
		n++;
		
		// calculate position of c in image and increment counter
		int2 pos;
		
		pos.x = (z.y-area.y) / area.w * size.x; 
		pos.y = (z.x-area.x) / area.z * size.y; 
		
		if (pos.x >= 0 && pos.x < size.x && pos.y >= 0 && pos.y < size.y) 
			atom_inc(&counters[size.x*pos.y+pos.x]);
	}
}

kernel void getBounds(const int2 size, global long* bounds, global long* counters) {
	if(get_global_id(0) >= size.y) 
		return;
	
	long myMin = (long)((ulong)1 << 63 - 1); 
	long myMax = 0; 
		
	for(int i = 0; i < size.x; i++) {
		long value = counters[get_global_id(0)*size.x+i];
		
		if(value < myMin)
			myMin = value;
			
		if(value > myMax)
			myMax = value; 
	}
	
	bounds[get_global_id(0)*2] = myMin; 
	bounds[get_global_id(0)*2 + 1] = myMax; 
}

kernel void paint(const int2 size, const long2 bounds, const int offsetY, global int* image, global long* counters, const int overExposure) { 
	int x = get_global_id(0);
	int y = get_global_id(1);
	
	if ( x >= size.x || y >= size.y)
		return;

	long count = counters[(get_global_id(1)+offsetY)*size.x + get_global_id(0)];
	int value = (int)(((fp) count - bounds.x) / (bounds.y - bounds.x) * 255);
	
	value *= overExposure; 
	
	if ( value > 255) 
		value = 255; 
	
	image[get_global_id(1)*size.x + get_global_id(0)] = value | value << 8 | value << 16;
}