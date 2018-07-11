
import sys, time, random

# N = int(sys.argv[1])
N = 2 ** 6

def mm_MG(X,Y,Z):
    # iterate through rows of X
    for i in range(len(X)):
       # iterate through columns of Y
       for j in range(len(Y[0])):
           # iterate through rows of Y
           for k in range(len(Y)):
               Z[i][j] += X[i][k] * Y[k][j]
    return Z


X = [[random.random() for i in range(N)] for j in range(N)]
Y = [[random.random() for i in range(N)] for j in range(N)]
Z = [[0.0 for i in range(N)] for j in range(N)]

print('Running matrix multiplication (%d x %d)..' % (N, N))
start = time.time()
mm_MG(X,Y,Z)
duration = "Matrix multiplication time: %.5f seconds" % ((time.time() - start))

print('Calculate maximum delta..')
delta = 0.0
for i in range(len(X)):
    # iterate through columns of Y
    for j in range(len(Y[0])):
        # iterate through rows of Y
        r = 0
        for k in range(len(Y)):
            r += X[i][k] * Y[k][j]

        r = abs(r - Z[i][j])
        delta = delta if r <= delta else r

if delta == 0.0:
    print("Identical result compare to ZipPy")
else:
    print('maximum delta = %f' % delta)
print(duration)
# for r in result:
#    print(r)
