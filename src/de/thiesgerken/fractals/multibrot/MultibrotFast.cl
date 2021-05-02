// inline fp iterate(const fp2 c, const int invert, const fp exponent, const int maxIterations, const fp bailoutSquared) {
// inline bool fastCheck(const fp2 c)

kernel void multibrot (	const int2 size,
						const fp4 area,
						const int maxIterations, 
						const fp bailoutSquared, 
						const fp exponent,
						const int invert,
						const int2 supersampling,
						const fp2 paletteOptions, // cycles, phase
						const int paletteLength,
						global int* palette,
						global int* image
					  ) {
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

			fp2 c = (fp2) (area.x + area.z * pos.x / size.x, area.y + area.w * pos.y / size.y);
			
			fp n = iterate (c, invert, exponent, maxIterations, bailoutSquared); 
					
			// smooth
			if (n < maxIterations) {
				int index = log10(n) * paletteLength * paletteOptions.x / log10((fp)maxIterations) + paletteOptions.y * paletteLength;
				int subColor = palette[index % paletteLength];
				
				color.x += (subColor & 255) / pxCount;
				color.y += ((subColor >> 8) & 255) / pxCount;
				color.z += ((subColor >> 16) & 255) / pxCount;
			}
		}
			
	image[get_global_id(1)*size.x + get_global_id(0)] = (int)color.x + ((int)color.y << 8) + ((int)color.z << 16);
}