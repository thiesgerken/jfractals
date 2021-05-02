// inline fp iterate(const fp2 c, const int invert, const fp exponent, const int maxIterations, const fp bailoutSquared) {
// inline bool fastCheck(const fp2 c)

kernel void multibrot (	const int2 size,
						const fp4 area,
						const int maxIterations, 
						const fp bailoutSquared, 
						const fp exponent,
						const int invert,
						const int2 supersampling,
						global fp* output
					  ) {
	int x = get_global_id(0);
	int y = get_global_id(1);
	
	if ( x >= size.x || y >= size.y)
		return;

	fp m = 0; 
	fp pxCount = supersampling.x*supersampling.y;

	for (int sx = 0; sx < supersampling.x; sx++)
		for (int sy = 0; sy < supersampling.y; sy++) {
		
			fp2 pos = (fp2) (x-0.5+((fp)1/supersampling.x * (sx+0.5)),
					 		 y-0.5+((fp)1/supersampling.y * (sy+0.5)));

			fp2 c = (fp2) (area.x + area.z * pos.x / size.x, area.y + area.w * pos.y / size.y);
			
			m += iterate(c, invert, exponent, maxIterations, bailoutSquared)/pxCount; 
		}
	
	if ( maxIterations - m > 1E-8 ) 
		output[get_global_id(1)*size.x + get_global_id(0)] = m;
	 else
		output[get_global_id(1)*size.x + get_global_id(0)] = -1;
}
				
kernel void color( const int2 size,
				   const fp2 paletteOptions, // cycles, phase
				   const int paletteLength,
				   global int* palette, 
				   const int minN,
				   const int maxN,
				   const long pxCount,
				   const fp ratio,
				   global fp* counts, 
				   global int* cdf,
				   global int* image ) {
	int x = get_global_id(0);
	int y = get_global_id(1);
	
	if ( x >= size.x || y >= size.y)
		return;

	int index = get_global_id(1)*size.x + get_global_id(0);

	fp m = counts[index]-minN;
	int n = (int) m;
	
	if ( m <= 0) 
		image[index] = 0;
	else {
		int fraction = cdf[n];
		int diff;
		
		if (n >= maxN-minN) 
			diff = 0; 
		else
			diff = cdf[n+1] - fraction; 
	
		fp percHist = ((fp)fraction + (m-n)*diff ) / pxCount;
		fp percQuot = m / (maxN - minN); 
		
		fp perc = percHist*ratio+percQuot*(1-ratio); 
		
		int pindex = (int) ( (  perc * paletteOptions.x  + paletteOptions.y ) * paletteLength );
		image[index] = palette[pindex % paletteLength];
	}				   
}				