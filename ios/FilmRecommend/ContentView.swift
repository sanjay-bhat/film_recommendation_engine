import SwiftUI

struct ContentView: View {
    @StateObject private var viewModel = MovieViewModel()

    var body: some View {
        SearchView(viewModel: viewModel)
            .task { viewModel.loadDataset() }
    }
}
