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

/* 
 * Performs a check on a point without having to iterate 
 */
inline bool fastCheck(const fp2 c) {
    fp cy2 = c.y*c.y;
   
    // Quick rejection check if c is in 2nd order period bulb
    if( (c.x+1.0) * (c.x+1.0) + cy2 < 0.0625) return true;

    // Quick rejection check if c is in main cardioid
    fp q = (c.x-0.25)*(c.x-0.25) + cy2;
    if( q*(q+(c.x-0.25)) < 0.25*cy2) return true; 
	
    // test for the smaller bulb left of the period-2 bulb
    if (( ((c.x+1.309)*(c.x+1.309)) + c.y*c.y) < 0.00345) return true;

    // check for the smaller bulbs on top and bottom of the cardioid
    if ((((c.x+0.125)*(c.x+0.125)) + (c.y-0.744)*(c.y-0.744)) < 0.0088) return true;
    if ((((c.x+0.125)*(c.x+0.125)) + (c.y+0.744)*(c.y+0.744)) < 0.0088) return true;

    return false;
}

inline fp iterate(fp2 c, const int invert, const fp exponent, const int maxIterations, const fp bailoutSquared) {
	int n = 0;
	fp aux = 0;
	fp2 z = (fp2) (0); 

    // should I invert the picture? (+ 1/c instead of +c)
	if (invert)	{
		aux = c.x*c.x+c.y*c.y; // = |c|^2
	
		if (aux) { 
			// dividing by zero is evil
				
			// replace c with 1/c via c*c' = |c|^2 
			// (where c' is the complex conjugate of c)
				
			c.x = c.x / aux;
			c.y = -c.y / aux;
		}
	}	
	   
	if (exponent == 2) { 
		// try to calculate the standard set as fast as possible
			
		if (fastCheck(c)) 
			n = maxIterations; 
		else
			while (n < maxIterations && z.x*z.x+z.y*z.y < bailoutSquared) {
				aux = 2 * z.x * z.y + c.y;
		    	z.x = z.x*z.x - z.y*z.y + c.x;
				z.y = aux;
				n++;
			}
	} else if (exponent - trunc(exponent) < 1E-10) {
		// integer exponent other than 2
		fp2 orig;
		int exp = (int)exponent;
	
		while (n < maxIterations && z.x*z.x+z.y*z.y < bailoutSquared) {
			if (exp < 0) {
				// invert z first
				aux = z.x*z.x+z.y*z.y;
	
				if (aux) { 
					// dividing by zero is evil
					z.x =  z.x / aux;
					z.y = -z.y / aux;
				}
			}
			
			orig = z;
						
			for (int i=1; i<abs(exp); i++) {
				aux = z.x * orig.y + z.y * orig.x;
				z.x = z.x * orig.x - z.y * orig.y;
				z.y = aux;		
			}
				
			if (!exp) 
				z = (fp2) (1,0);
				
			z.x += c.x;
			z.y += c.y;
	
			n++;
		}
	} else { 
		// exponent is a float
			
		fp2 ln; 
		fp ex;
	
		while (n < maxIterations && z.x*z.x+z.y*z.y < bailoutSquared) {
			// raise z by exponent
				
			if (z.x != 0 || z.y != 0) {
				// 0^k = 0 forall k in R \ Z
			
				// z^k = e^(k*ln(z))
				// e^(x+yi) = e^x * e^(yi) = e^x * (cos(y) + sin(y) * i)
				// ln(z) = |z| + phi*i 
	
				ln.x = sqrt(z.x*z.x+z.y*z.y);
				ln.y = acos(z.x / ln.x);
				ln.x = log(ln.x);
	
				if (z.y < 0) 
					ln.y = -ln.y;
					
				if (ln.y < 0)
					ln.y = ln.y + M_PI * 2;
	
				ln.x = exponent * ln.x;
				ln.y = exponent * ln.y;
						
				ex = exp(ln.x);
	
				z.x = ex * cos(ln.y);
				z.y = ex * sin(ln.y);
			}
	
			z.x = z.x + c.x;
			z.y = z.y + c.y;

			n++;
		}
	}
	
	// smooth
	if (n < maxIterations) 
		// mu(z) = n - log_k (log|z_n|/log(bailout))
		return  n+1-log10(log10(length(z)))/log10(fabs(exponent)); 
	else 
		return n; 				
}
